package cn.tolim.pudans.core.creation.provider;

import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiGenerationModelCatalog implements GenerationModelCatalog {

    private static final int MAX_PROMPT_LENGTH = 20_000;

    private static final int MAX_INPUT_ASSETS = 10;

    private final AiModelRepository models;

    private final AiChannelModelRepository channelModels;

    private final AiChannelRepository channels;

    @Override
    public Mono<GenerationModelProfile> resolve(UUID modelId, String workspaceId) {
        if (modelId == null || !StringUtils.hasText(workspaceId)) {
            return Mono.empty();
        }
        return models.findById(modelId)
                .filter(model -> AiSupport.ACTIVE.equals(model.status()))
                .flatMap(model -> {
                    var mediaTypes = mediaTypes(model);
                    if (mediaTypes.isEmpty()) {
                        return Mono.empty();
                    }
                    return channelModels
                            .findEnabledRoute(modelId, workspaceId.trim())
                            .flatMap(route -> channels
                                    .findById(route.channelId())
                                    .filter(channel -> channel.getWorkspaceId() != null
                                            && channel.getWorkspaceId().equals(workspaceId.trim()))
                                    .filter(channel -> AiSupport.ACTIVE.equals(channel.getStatus()))
                                    .map(channel -> profile(
                                            model,
                                            route,
                                            channel.getId(),
                                            channel.getProvider(),
                                            mediaTypes)));
                });
    }

    @Override
    public Mono<Void> validate(
            GenerationModelProfile profile,
            GenerationTask.MediaType mediaType,
            GenerationTask.Operation operation,
            String prompt,
            Map<String, Object> settings,
            List<String> inputAssetIds) {
        return Mono.defer(() -> {
            if (profile == null
                    || !profile.mediaTypes().contains(mediaType)
                    || !profile.operations().contains(operation)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "generation-model-capability-unsupported",
                        "当前模型不支持该媒体类型或生成操作。");
            }
            if (prompt != null && prompt.length() > MAX_PROMPT_LENGTH) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "generation-prompt-too-long",
                        "生成提示词长度不能超过 " + MAX_PROMPT_LENGTH + "。");
            }
            if (inputAssetIds != null && inputAssetIds.size() > MAX_INPUT_ASSETS) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "generation-input-assets-too-many",
                        "单次生成最多引用 " + MAX_INPUT_ASSETS + " 个输入资产。");
            }
            if (operation != GenerationTask.Operation.TEXT
                    && (inputAssetIds == null || inputAssetIds.isEmpty())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "generation-input-asset-required",
                        "该生成操作需要至少一个输入资产。");
            }
            validateQuantity(settings);
            return Mono.empty();
        });
    }

    private static GenerationModelProfile profile(
            AiModel model,
            AiChannelModel route,
            UUID channelId,
            String provider,
            Set<GenerationTask.MediaType> mediaTypes) {
        var operations = new LinkedHashSet<GenerationTask.Operation>();
        mediaTypes.forEach(mediaType -> operations.addAll(operations(model, mediaType)));
        return new GenerationModelProfile(
                model.id(),
                provider,
                route.upstreamModelKey(),
                channelId,
                mediaTypes,
                operations);
    }

    private static Set<GenerationTask.MediaType> mediaTypes(AiModel model) {
        var types = new LinkedHashSet<GenerationTask.MediaType>();
        var capabilities = model.capabilities() == null ? Map.<String, Object>of() : model.capabilities();
        if (capabilities.containsKey("image")) {
            types.add(GenerationTask.MediaType.IMAGE);
        }
        if (capabilities.containsKey("video")) {
            types.add(GenerationTask.MediaType.VIDEO);
        }
        return Set.copyOf(types);
    }

    private static Set<GenerationTask.Operation> operations(
            AiModel model, GenerationTask.MediaType mediaType) {
        var explicit = explicitOperations(model, mediaType);
        if (!explicit.isEmpty()) {
            return explicit;
        }
        var id = normalize(model.logicalName() + " " + model.displayName());
        if (mediaType == GenerationTask.MediaType.IMAGE) {
            if (id.contains("edit") || id.contains("qwen-image-2") || id.contains("qwen-image-3")) {
                return Set.of(
                        GenerationTask.Operation.TEXT,
                        GenerationTask.Operation.EDIT,
                        GenerationTask.Operation.REFERENCE);
            }
            return Set.of(GenerationTask.Operation.TEXT);
        }
        if (id.contains("videoedit") || id.contains("video-edit")) {
            return Set.of(
                    GenerationTask.Operation.EDIT,
                    GenerationTask.Operation.REFERENCE);
        }
        if (id.contains("r2v")) {
            return Set.of(
                    GenerationTask.Operation.TEXT,
                    GenerationTask.Operation.REFERENCE);
        }
        if (id.contains("i2v")) {
            return Set.of(
                    GenerationTask.Operation.TEXT,
                    GenerationTask.Operation.REFERENCE,
                    GenerationTask.Operation.FRAME);
        }
        return Set.of(GenerationTask.Operation.TEXT);
    }

    private static Set<GenerationTask.Operation> explicitOperations(
            AiModel model, GenerationTask.MediaType mediaType) {
        var schema = model.parameterSchema();
        if (schema == null || schema.isEmpty()) {
            return Set.of();
        }
        var values = listValue(schema.get("operations"));
        if (values.isEmpty() && schema.get("mediaTypes") instanceof Map<?, ?> mediaTypes) {
            var entry = mediaTypes.get(mediaType.getCode());
            if (entry instanceof Map<?, ?> details) {
                values = listValue(details.get("operations"));
            }
        }
        var operations = new LinkedHashSet<GenerationTask.Operation>();
        for (var value : values) {
            try {
                operations.add(GenerationTask.Operation.fromCode(value));
            } catch (IllegalArgumentException ignored) {
                // 目录中的未知操作不进入可执行能力。
            }
        }
        return Set.copyOf(operations);
    }

    private static List<String> listValue(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var item : values) {
            if (item != null && StringUtils.hasText(item.toString())) {
                result.add(item.toString().trim());
            }
        }
        return List.copyOf(result);
    }

    private static void validateQuantity(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return;
        }
        var value = settings.get("n");
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)
                || number.intValue() < 1
                || number.intValue() > 9) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "generation-quantity-invalid",
                    "生成数量必须在 1 到 9 之间。");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
