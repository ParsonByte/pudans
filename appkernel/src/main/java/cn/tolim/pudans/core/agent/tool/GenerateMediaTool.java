package cn.tolim.pudans.core.agent.tool;

import cn.tolim.pudans.contract.creation.dto.GenerationTaskStartRequest;
import cn.tolim.pudans.core.creation.task.GenerationTasks;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.Digests;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GenerateMediaTool {

    public static final String NAME = "generate_media";

    private static final String DEFAULT_OPERATION = "text";

    private final GenerationTasks tasks;

    // 向聊天模型声明真实的媒体生成工具参数。
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description(
                        "Create a real image or video generation task. Returns the persisted task id and status.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("media_type", "Media type: image or video.")
                        .addStringProperty("operation", "Generation operation, defaults to text.")
                        .addStringProperty(
                                "model_id",
                                "Catalog model UUID for the requested media type.")
                        .addStringProperty("prompt", "Complete generation prompt.")
                        .addProperty(
                                "settings",
                                JsonObjectSchema.builder()
                                        .description("Model-specific generation settings.")
                                        .additionalProperties(true)
                                        .build())
                        .addProperty(
                                "input_asset_ids",
                                JsonArraySchema.builder()
                                        .description("Existing asset ids used as references.")
                                        .items(JsonStringSchema.builder().build())
                                        .build())
                        .required("media_type", "model_id", "prompt")
                        .build())
                .build();
    }

    // 使用真实生成任务服务执行工具并把任务摘要返回给模型。
    public Mono<Map<String, Object>> execute(
            AgentToolContext toolContext, Map<String, Object> arguments) {
        return Mono.defer(() -> {
            if (toolContext == null) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "agent-tool-context-missing",
                        "生成工具缺少运行上下文。"));
            }
            var values = arguments == null ? Map.<String, Object>of() : arguments;
            var mediaType = string(values.get("media_type"));
            var operation = string(values.get("operation"));
            var modelId = string(values.get("model_id"));
            var prompt = string(values.get("prompt"));
            if (!StringUtils.hasText(mediaType)
                    || !StringUtils.hasText(modelId)
                    || !StringUtils.hasText(prompt)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "agent-media-request-invalid",
                        "媒体类型、模型和提示词不能为空。"));
            }
            UUID model;
            try {
                model = UUID.fromString(modelId.trim());
            } catch (IllegalArgumentException error) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "agent-media-model-invalid",
                        "生成模型标识不是有效 UUID。"));
            }
            var resolvedOperation = StringUtils.hasText(operation)
                    ? operation.trim()
                    : DEFAULT_OPERATION;
            var request = new GenerationTaskStartRequest(
                    mediaType.trim(),
                    resolvedOperation,
                    model,
                    prompt.trim(),
                    settings(values.get("settings")),
                    assetIds(values.get("input_asset_ids")),
                    toolContext.sessionId(),
                    toolContext.runId(),
                    null,
                    null,
                    toolContext.quotaReservationId());
            return tasks
                    .start(
                            new RequestIdentity(
                                    toolContext.ownerId(), toolContext.workspaceId()),
                            request,
                            idempotencyKey(toolContext, request))
                    .map(result -> summary(result.task()));
        });
    }

    private static Map<String, Object> settings(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        var settings = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> settings.put(String.valueOf(key), item));
        return settings;
    }

    private static List<String> assetIds(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(GenerateMediaTool::string)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String idempotencyKey(
            AgentToolContext context, GenerationTaskStartRequest request) {
        var fingerprint = JsonUtils.objectToJson(Map.of(
                "mediaType", request.mediaType(),
                "operation", request.operation(),
                "modelId", request.modelId(),
                "prompt", request.prompt(),
                "settings", request.settings(),
                "inputAssetIds", request.inputAssetIds()));
        return "agent-media:" + Digests.sha256Hex(context.runId() + "\0" + fingerprint);
    }

    private static Map<String, Object> summary(
            cn.tolim.pudans.contract.creation.dto.GenerationTaskSummary task) {
        var result = new LinkedHashMap<String, Object>();
        result.put("taskId", task.id());
        result.put("mediaType", task.mediaType());
        result.put("operation", task.operation());
        result.put("modelId", task.modelId());
        result.put("status", task.status().getCode());
        result.put("providerRequestSent", task.providerRequestSent());
        result.put("resultAssetIds", task.resultAssetIds());
        return result;
    }
}