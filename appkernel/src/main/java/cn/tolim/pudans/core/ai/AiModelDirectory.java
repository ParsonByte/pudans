package cn.tolim.pudans.core.ai;

import cn.tolim.pudans.contract.ai.AiDefaultModel;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiDefaultModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiModelDirectory {

    public static final String AUTO = "auto";

    private final AiDefaultModelRepository defaults;

    private final AiModelRepository models;

    public Mono<AiModel> resolveDefault(String workspaceId, String capability) {
        var normalized = AiSupport.normalizeCapability(capability);
        return defaults
                .findByWorkspaceAndCapability(workspaceId, normalized)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ai-model-default-missing",
                        "当前工作区尚未配置该能力的默认模型。")))
                .map(AiDefaultModel::modelId)
                .flatMap(modelId -> requireAvailable(workspaceId, modelId, normalized));
    }

    public Mono<AiModel> resolve(String workspaceId, String requestedModel, String capability) {
        if (!StringUtils.hasText(requestedModel) || AUTO.equalsIgnoreCase(requestedModel.trim())) {
            return resolveDefault(workspaceId, capability);
        }
        UUID modelId;
        try {
            modelId = UUID.fromString(requestedModel.trim());
        } catch (IllegalArgumentException error) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "ai-model-id-invalid", "模型标识不是有效 UUID。"));
        }
        return requireAvailable(workspaceId, modelId, capability);
    }

    private Mono<AiModel> requireAvailable(
            String workspaceId, UUID modelId, String capability) {
        return models
                .findById(modelId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "ai-model-unavailable",
                        "默认模型不存在或已停用。")))
                .flatMap(model -> {
                    if (!AiSupport.ACTIVE.equals(model.status())) {
                        return Mono.error(unavailable());
                    }
                    if (model.capabilities() == null
                            || !model.capabilities().containsKey(capability)) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "ai-model-capability-missing",
                                "模型不支持请求的能力。"));
                    }
                    return models
                            .existsAvailable(modelId, workspaceId)
                            .flatMap(available -> Boolean.TRUE.equals(available)
                                    ? Mono.just(model)
                                    : Mono.error(unavailable()));
                });
    }

    private static ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ai-model-unavailable",
                "模型在当前工作区没有可用渠道。");
    }
}
