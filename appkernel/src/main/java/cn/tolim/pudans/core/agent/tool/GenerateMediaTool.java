package cn.tolim.pudans.core.agent.tool;

import cn.tolim.pudans.contract.creation.dto.GenerationTaskStartRequest;
import cn.tolim.pudans.core.creation.task.GenerationTasks;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.Digests;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
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

    @Tool(
            name = NAME,
            description = "Create a real image or video generation task. Returns the persisted task id and status.",
            readOnly = false,
            concurrencySafe = false)
    public Mono<Map<String, Object>> generateMedia(
            @ToolParam(
                            name = "media_type",
                            required = true,
                            description = "Media type: image or video.")
                    String mediaType,
            @ToolParam(
                            name = "operation",
                            required = false,
                            description = "Generation operation, defaults to text.")
                    String operation,
            @ToolParam(
                            name = "model_id",
                            required = true,
                            description = "Catalog model UUID for the requested media type.")
                    String modelId,
            @ToolParam(
                            name = "prompt",
                            required = true,
                            description = "Complete generation prompt.")
                    String prompt,
            @ToolParam(
                            name = "settings",
                            required = false,
                            description = "Model-specific generation settings.")
                    Map<String, Object> settings,
            @ToolParam(
                            name = "input_asset_ids",
                            required = false,
                            description = "Existing asset ids used as references.")
                    List<String> inputAssetIds,
            RuntimeContext runtimeContext) {
        return Mono.defer(() -> {
            var toolContext = runtimeContext == null
                    ? null
                    : runtimeContext.get(AgentToolContext.class);
            if (toolContext == null) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "agent-tool-context-missing",
                        "生成工具缺少运行上下文。"));
            }
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
            Map<String, Object> resolvedSettings = settings == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(settings);
            List<String> resolvedAssets = inputAssetIds == null ? List.of() : inputAssetIds;
            var request = new GenerationTaskStartRequest(
                    mediaType.trim(),
                    resolvedOperation,
                    model,
                    prompt.trim(),
                    resolvedSettings,
                    resolvedAssets,
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
