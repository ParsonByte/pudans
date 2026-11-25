package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.formatter.JsonSchema;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.Source;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ModelException;
import io.agentscope.core.model.ToolChoice;
import io.agentscope.core.model.ToolSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public class OpenAiResponsesCodec {

    private static final String JSON_SCHEMA = "json_schema";

    private static final String FUNCTION_CALL = "function_call";

    private static final String OUTPUT_TEXT = "output_text";

    private static final String MINIMAX = "minimax";

    private static final String DEEPSEEK = "deepseek";

    private static final String DASHSCOPE = "dashscope";

    private static final String PROVIDER_OUTPUT_ITEMS = "providerOutputItems";

    private static final Set<String> MINIMAX_REASONING_EFFORTS =
            Set.of("none", "minimal", "low", "medium", "high");

    private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() {};

    public Map<String, Object> request(
            String providerId,
            String modelId,
            List<Msg> messages,
            List<ToolSchema> tools,
            GenerateOptions options,
            Integer fallbackMaxTokens) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("input", inputItems(providerId, messages));
        var instructions = systemText(messages);
        if (!instructions.isBlank()) {
            body.put("instructions", instructions);
        }
        put(
                body,
                "max_output_tokens",
                options == null ? fallbackMaxTokens : maxOutputTokens(options, fallbackMaxTokens));
        if (options != null) {
            put(body, "temperature", temperature(providerId, options.getTemperature()));
            put(body, "top_p", topP(providerId, options.getTopP()));
            if (options.getReasoningEffort() != null) {
                put(body, "reasoning", reasoning(providerId, options.getReasoningEffort()));
            }
            put(body, "parallel_tool_calls", parallelToolCalls(providerId, options.getParallelToolCalls()));
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools(tools));
        }
        var toolChoice = toolChoice(providerId, options == null ? null : options.getToolChoice());
        if (toolChoice != null) {
            body.put("tool_choice", toolChoice);
        }
        applyResponseFormat(body, options);
        if (DASHSCOPE.equals(providerId)) {
            body.put("store", false);
        }
        if (DEEPSEEK.equals(providerId)) {
            normalizeDeepSeek(body);
        }
        body.put("stream", true);
        return Map.copyOf(body);
    }

    public StreamDecoder streamDecoder(String modelName, String providerId) {
        return new StreamDecoder(modelName, providerId);
    }

    private static List<Map<String, Object>> inputItems(
            String providerId, List<Msg> messages) {
        var items = new ArrayList<Map<String, Object>>();
        if (messages == null) {
            return List.of();
        }
        for (var message : messages) {
            switch (message.getRole()) {
                case USER -> items.add(userItem(providerId, message.getContent()));
                case ASSISTANT -> items.addAll(assistantItems(providerId, message));
                case TOOL -> items.addAll(toolResultItems(message.getContent()));
                case SYSTEM -> {}
            }
        }
        return List.copyOf(items);
    }

    private static Map<String, Object> userItem(
            String providerId, List<ContentBlock> blocks) {
        var content = new ArrayList<Map<String, Object>>();
        for (var block : blocks) {
            if (block instanceof TextBlock text) {
                content.add(Map.of("type", "input_text", "text", text.getText()));
            } else {
                content.add(mediaItem(providerId, block));
            }
        }
        return Map.of("role", "user", "content", List.copyOf(content));
    }

    private static List<Map<String, Object>> assistantItems(
            String providerId, Msg message) {
        var items = new ArrayList<Map<String, Object>>();
        items.addAll(replayedItems(providerId, message.getMetadata()));
        for (var block : message.getContent()) {
            if (block instanceof ToolUseBlock tool) {
                items.add(Map.of(
                        "type", FUNCTION_CALL,
                        "call_id", tool.getId(),
                        "name", tool.getName(),
                        "arguments", toolContent(tool)));
            }
        }
        var text = new StringBuilder();
        for (var block : message.getContent()) {
            if (block instanceof TextBlock value) {
                text.append(value.getText());
            }
        }
        if (!text.isEmpty()) {
            items.add(Map.of(
                    "role",
                    "assistant",
                    "content",
                    List.of(Map.of("type", OUTPUT_TEXT, "text", text.toString()))));
        }
        return List.copyOf(items);
    }

    private static List<Map<String, Object>> replayedItems(
            String providerId, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        var value = metadata.get(PROVIDER_OUTPUT_ITEMS);
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var allowed = DEEPSEEK.equals(providerId)
                ? Set.of("reasoning", "web_search_call")
                : Set.of("reasoning");
        var replayed = new ArrayList<Map<String, Object>>();
        for (var item : items) {
            if (!(item instanceof Map<?, ?> map) || !allowed.contains(map.get("type"))) {
                continue;
            }
            var normalized = new LinkedHashMap<String, Object>();
            map.forEach((key, fieldValue) -> normalized.put(String.valueOf(key), fieldValue));
            replayed.add(Map.copyOf(normalized));
        }
        return List.copyOf(replayed);
    }

    private static List<Map<String, Object>> toolResultItems(List<ContentBlock> blocks) {
        var items = new ArrayList<Map<String, Object>>();
        for (var block : blocks) {
            if (block instanceof ToolResultBlock result) {
                items.add(Map.of(
                        "type", "function_call_output",
                        "call_id", result.getId(),
                        "output", textOutput(result.getOutput())));
            }
        }
        return List.copyOf(items);
    }

    private static Map<String, Object> mediaItem(String providerId, ContentBlock block) {
        var source = source(block);
        if (source == null) {
            throw unsupported("Responses 消息包含不支持的媒体块。");
        }
        var mediaType = mediaType(source);
        if (mediaType.startsWith("image/")) {
            return Map.of("type", "input_image", "image_url", mediaData(source));
        }
        if (mediaType.startsWith("video/")) {
            if (DEEPSEEK.equals(providerId)) {
                throw unsupported("DeepSeek Responses 仅支持图片输入。");
            }
            if (MINIMAX.equals(providerId) || DASHSCOPE.equals(providerId)) {
                return Map.of("type", "input_video", "video_url", mediaData(source));
            }
            return Map.of(
                    "type", "input_file", "filename", "input", "file_data", mediaData(source));
        }
        return Map.of(
                "type", "input_file", "filename", "input", "file_data", mediaData(source));
    }

    private static String mediaData(Source source) {
        if (source instanceof Base64Source base64) {
            return "data:" + base64.getMediaType() + ";base64," + base64.getData();
        }
        if (source instanceof URLSource url) {
            return url.getUrl();
        }
        throw unsupported("Responses 消息媒体源不受支持。");
    }

    private static String mediaType(Source source) {
        if (source instanceof Base64Source base64) {
            return base64.getMediaType();
        }
        if (source instanceof URLSource url && url.getMimeType() != null) {
            return url.getMimeType();
        }
        return "application/octet-stream";
    }

    private static Source source(ContentBlock block) {
        if (block instanceof DataBlock data) {
            return data.getSource();
        }
        if (block instanceof ImageBlock image) {
            return image.getSource();
        }
        return null;
    }

    private static String systemText(List<Msg> messages) {
        var system = new StringBuilder();
        if (messages == null) {
            return "";
        }
        for (var message : messages) {
            if (message.getRole() != MsgRole.SYSTEM) {
                continue;
            }
            for (var block : message.getContent()) {
                if (block instanceof TextBlock text && !text.getText().isBlank()) {
                    if (!system.isEmpty()) {
                        system.append("\n\n");
                    }
                    system.append(text.getText());
                }
            }
        }
        return system.toString();
    }

    private static List<Map<String, Object>> tools(List<ToolSchema> tools) {
        var values = new ArrayList<Map<String, Object>>();
        for (var tool : tools) {
            var value = new LinkedHashMap<String, Object>();
            value.put("type", "function");
            value.put("name", tool.getName());
            value.put("description", tool.getDescription());
            value.put("parameters", normalizeSchema(tool.getParameters()));
            values.add(Map.copyOf(value));
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> normalizeSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        return schema;
    }

    private static Object toolChoice(String providerId, ToolChoice choice) {
        if (choice == null) {
            return null;
        }
        if (MINIMAX.equals(providerId)) {
            return switch (choice) {
                case ToolChoice.None ignored -> "none";
                case ToolChoice.Auto ignored -> "auto";
                default -> throw new IllegalArgumentException(
                        "MiniMax Responses tool_choice 只支持 auto 或 none。");
            };
        }
        return switch (choice) {
            case ToolChoice.Auto ignored -> "auto";
            case ToolChoice.None ignored -> "none";
            case ToolChoice.Required ignored -> "required";
            case ToolChoice.Specific specific ->
                Map.of("type", "function", "name", specific.toolName());
        };
    }

    private static void applyResponseFormat(Map<String, Object> body, GenerateOptions options) {
        if (options == null || options.getResponseFormat() == null) {
            return;
        }
        var format = options.getResponseFormat();
        if (!JSON_SCHEMA.equals(format.getType())) {
            return;
        }
        JsonSchema schema = format.getJsonSchema();
        if (schema == null || schema.getSchema() == null || schema.getSchema().isEmpty()) {
            throw new IllegalArgumentException("Responses JSON Schema 输出需要非空 schema。");
        }
        var value = new LinkedHashMap<String, Object>();
        value.put("type", JSON_SCHEMA);
        value.put("name", schema.getName() == null ? "response" : schema.getName());
        value.put("schema", schema.getSchema());
        value.put("strict", Boolean.TRUE.equals(schema.getStrict()));
        body.put("text", Map.of("format", Map.copyOf(value)));
    }

    private static Integer maxOutputTokens(GenerateOptions options, Integer fallback) {
        if (options.getMaxCompletionTokens() != null) {
            return options.getMaxCompletionTokens();
        }
        if (options.getMaxTokens() != null) {
            return options.getMaxTokens();
        }
        return fallback != null && fallback > 0 ? fallback : null;
    }

    private static Double temperature(String providerId, Double temperature) {
        if (temperature == null || !MINIMAX.equals(providerId)) {
            return temperature;
        }
        if (temperature < 0 || temperature > 1) {
            throw new IllegalArgumentException("MiniMax Responses temperature 必须在 0 到 1 之间。");
        }
        return temperature;
    }

    private static Double topP(String providerId, Double topP) {
        if (topP == null) {
            return null;
        }
        if (MINIMAX.equals(providerId) && (topP < 0 || topP > 1)) {
            throw new IllegalArgumentException("MiniMax Responses top_p 必须在 0 到 1 之间。");
        }
        return topP;
    }

    private static Map<String, Object> reasoning(String providerId, String effort) {
        if (MINIMAX.equals(providerId) && !MINIMAX_REASONING_EFFORTS.contains(effort)) {
            throw new IllegalArgumentException(
                    "MiniMax Responses reasoning effort 必须是 none、minimal、low、medium 或 high。");
        }
        return Map.of("effort", effort);
    }

    private static Boolean parallelToolCalls(String providerId, Boolean value) {
        if (MINIMAX.equals(providerId) && value != null) {
            throw new IllegalArgumentException(
                    "MiniMax Responses 不支持 parallel_tool_calls。");
        }
        return value;
    }

    private static void normalizeDeepSeek(Map<String, Object> body) {
        for (var field : List.of(
                "store",
                "previous_response_id",
                "conversation",
                "background",
                "metadata",
                "include",
                "prompt",
                "truncation",
                "service_tier",
                "safety_identifier",
                "prompt_cache_key",
                "prompt_cache_retention",
                "context_management",
                "parallel_tool_calls",
                "max_tool_calls",
                "stream_options")) {
            body.remove(field);
        }
        if (body.get("tools") instanceof List<?> tools) {
            var normalized = new ArrayList<Object>();
            for (var tool : tools) {
                if (tool instanceof Map<?, ?> map) {
                    var value = new LinkedHashMap<String, Object>();
                    map.forEach((key, fieldValue) -> value.put(String.valueOf(key), fieldValue));
                    value.remove("strict");
                    normalized.add(Map.copyOf(value));
                } else {
                    normalized.add(tool);
                }
            }
            body.put("tools", List.copyOf(normalized));
        }
    }

    private static void put(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    private static String toolContent(ToolUseBlock block) {
        if (block.getContent() != null && !block.getContent().isBlank()) {
            return block.getContent();
        }
        return JsonUtils.objectToJson(block.getInput() == null ? Map.of() : block.getInput());
    }

    private static String textOutput(List<ContentBlock> blocks) {
        var text = new StringBuilder();
        for (var block : blocks) {
            if (block instanceof TextBlock value) {
                text.append(value.getText());
            }
        }
        return text.toString();
    }

    private static String text(JsonNode node) {
        return node.isTextual() ? node.textValue() : "";
    }

    private static Integer integer(JsonNode node) {
        return node.isNumber() ? node.intValue() : null;
    }

    private static ApiException unsupported(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "ai-responses-content-unsupported", message);
    }

    public final class StreamDecoder {

        private final String modelName;

        private final String providerId;

        private final Instant startedAt = Instant.now();

        private final Map<Integer, ToolState> tools = new LinkedHashMap<>();

        private String responseId;

        private Integer inputTokens;

        private Integer outputTokens;

        private Integer cachedTokens;

        private boolean sawText;

        private boolean sawThinking;

        private boolean sawTool;

        private StreamDecoder(String modelName, String providerId) {
            this.modelName = modelName;
            this.providerId = providerId;
        }

        public List<ChatResponse> accept(String data) {
            if (data == null || data.isBlank() || "[DONE]".equals(data.trim())) {
                return List.of();
            }
            try {
                var event = JsonUtils.mapper().readTree(data);
                var type = text(event.path("type"));
                return switch (type) {
                    case "response.created", "response.in_progress" -> created(event);
                    case "response.output_text.delta" -> textDelta(event);
                    case "response.reasoning_summary_text.delta",
                            "response.reasoning_text.delta" -> thinkingDelta(event);
                    case "response.output_item.added" -> itemAdded(event);
                    case "response.function_call_arguments.delta" -> toolDelta(event);
                    case "response.output_item.done" -> itemDone(event);
                    case "response.completed", "response.incomplete" -> completed(event);
                    case "response.failed", "error" -> throw failure(event);
                    default -> List.of();
                };
            } catch (Exception error) {
                if (error instanceof ModelException modelError) {
                    throw modelError;
                }
                throw new ModelException(
                        "无法解析 Responses 流事件。", error, modelName, providerId);
            }
        }

        private List<ChatResponse> created(JsonNode event) {
            var id = text(event.path("response").path("id"));
            if (!id.isBlank()) {
                responseId = id;
            }
            return List.of(response(List.of(), null));
        }

        private List<ChatResponse> textDelta(JsonNode event) {
            var delta = text(event.path("delta"));
            if (delta.isEmpty()) {
                return List.of();
            }
            sawText = true;
            return List.of(response(List.of(TextBlock.builder().text(delta).build()), null));
        }

        private List<ChatResponse> thinkingDelta(JsonNode event) {
            var delta = text(event.path("delta"));
            if (delta.isEmpty()) {
                return List.of();
            }
            sawThinking = true;
            return List.of(response(
                    List.of(new ThinkingBlock.Builder().thinking(delta).build()), null));
        }

        private List<ChatResponse> itemAdded(JsonNode event) {
            var item = event.path("item");
            if (!FUNCTION_CALL.equals(text(item.path("type")))) {
                return List.of();
            }
            var index = event.path("output_index").asInt();
            var tool = new ToolState(
                    toolId(item), text(item.path("name")));
            tools.put(index, tool);
            sawTool = true;
            return List.of(response(
                    List.of(ToolUseBlock.builder()
                            .id(tool.id)
                            .name(tool.name)
                            .input(Map.of())
                            .build()),
                    null));
        }

        private List<ChatResponse> toolDelta(JsonNode event) {
            var index = event.path("output_index").asInt();
            var state = tools.computeIfAbsent(index, ignored -> new ToolState("", ""));
            var delta = text(event.path("delta"));
            if (delta.isEmpty()) {
                return List.of();
            }
            state.streamed = true;
            return List.of(response(
                    List.of(ToolUseBlock.builder()
                            .id("")
                            .name("__fragment__")
                            .input(Map.of())
                            .content(delta)
                            .build()),
                    null));
        }

        private List<ChatResponse> itemDone(JsonNode event) {
            var item = event.path("item");
            if (!FUNCTION_CALL.equals(text(item.path("type")))) {
                return List.of();
            }
            var index = event.path("output_index").asInt();
            var state = tools.get(index);
            if (state == null || state.streamed) {
                return List.of();
            }
            var arguments = text(item.path("arguments"));
            var input = arguments.isBlank() ? Map.<String, Object>of() : parseObject(arguments);
            sawTool = true;
            return List.of(response(
                    List.of(ToolUseBlock.builder()
                            .id(toolId(item))
                            .name(text(item.path("name")))
                            .input(input)
                            .content(arguments)
                            .build()),
                    null));
        }

        private List<ChatResponse> completed(JsonNode event) {
            var response = event.path("response");
            var id = text(response.path("id"));
            if (!id.isBlank()) {
                responseId = id;
            }
            var usage = response.path("usage");
            inputTokens = integer(usage.path("input_tokens"));
            outputTokens = integer(usage.path("output_tokens"));
            cachedTokens = integer(usage.path("input_tokens_details").path("cached_tokens"));
            var content = new ArrayList<ContentBlock>();
            if (!sawText) {
                content.addAll(outputText(response.path("output")));
            }
            if (!sawThinking) {
                content.addAll(reasoningText(response.path("output")));
            }
            if (!sawTool) {
                content.addAll(outputTools(response.path("output")));
            }
            var providerItems = providerOutputItems(response.path("output"));
            var metadata = providerItems.isEmpty()
                    ? null
                    : Map.<String, Object>of(PROVIDER_OUTPUT_ITEMS, providerItems);
            return List.of(response(
                    List.copyOf(content), text(response.path("status")), metadata));
        }

        private List<ContentBlock> outputText(JsonNode output) {
            var blocks = new ArrayList<ContentBlock>();
            if (!output.isArray()) {
                return List.of();
            }
            for (var item : output) {
                if (!"message".equals(text(item.path("type")))) {
                    continue;
                }
                for (var part : item.path("content")) {
                    if (OUTPUT_TEXT.equals(text(part.path("type")))) {
                        var value = text(part.path("text"));
                        if (!value.isEmpty()) {
                            sawText = true;
                            blocks.add(TextBlock.builder().text(value).build());
                        }
                    }
                }
            }
            return List.copyOf(blocks);
        }

        private List<ContentBlock> reasoningText(JsonNode output) {
            var blocks = new ArrayList<ContentBlock>();
            if (!output.isArray()) {
                return List.of();
            }
            for (var item : output) {
                if (!"reasoning".equals(text(item.path("type")))) {
                    continue;
                }
                var text = new StringBuilder();
                for (var part : item.path("summary")) {
                    text.append(text(part.path("text")));
                }
                for (var part : item.path("content")) {
                    var value = text(part.path("text"));
                    if (value.isEmpty()) {
                        value = text(part.path("reasoning_text"));
                    }
                    text.append(value);
                }
                if (!text.isEmpty()) {
                    sawThinking = true;
                    blocks.add(new ThinkingBlock.Builder()
                            .thinking(text.toString())
                            .build());
                }
            }
            return List.copyOf(blocks);
        }

        private List<ContentBlock> outputTools(JsonNode output) {
            var blocks = new ArrayList<ContentBlock>();
            if (!output.isArray()) {
                return List.of();
            }
            for (var item : output) {
                if (!FUNCTION_CALL.equals(text(item.path("type")))) {
                    continue;
                }
                var arguments = text(item.path("arguments"));
                sawTool = true;
                blocks.add(ToolUseBlock.builder()
                        .id(toolId(item))
                        .name(text(item.path("name")))
                        .input(arguments.isBlank() ? Map.of() : parseObject(arguments))
                        .content(arguments)
                        .build());
            }
            return List.copyOf(blocks);
        }

        private List<Map<String, Object>> providerOutputItems(JsonNode output) {
            var items = new ArrayList<Map<String, Object>>();
            if (!output.isArray()) {
                return List.of();
            }
            for (var item : output) {
                var type = text(item.path("type"));
                if (!"reasoning".equals(type) && !"web_search_call".equals(type)) {
                    continue;
                }
                items.add(JsonUtils.jsonToObject(item.toString(), OBJECT_TYPE));
            }
            return List.copyOf(items);
        }

        private ChatResponse response(List<ContentBlock> content, String status) {
            return response(content, status, null);
        }

        private ChatResponse response(
                List<ContentBlock> content, String status, Map<String, Object> metadata) {
            return ChatResponse.builder()
                    .id(responseId)
                    .content(content)
                    .usage(usage())
                    .metadata(metadata)
                    .finishReason(finishReason(status))
                    .build();
        }

        private ChatUsage usage() {
            if (inputTokens == null && outputTokens == null) {
                return null;
            }
            return ChatUsage.builder()
                    .inputTokens(inputTokens == null ? 0 : inputTokens)
                    .outputTokens(outputTokens == null ? 0 : outputTokens)
                    .cachedTokens(cachedTokens == null ? 0 : cachedTokens)
                    .time(Duration.between(startedAt, Instant.now()).toMillis() / 1000.0)
                    .build();
        }

        private Map<String, Object> parseObject(String value) {
            try {
                return JsonUtils.jsonToObject(value, OBJECT_TYPE);
            } catch (RuntimeException error) {
                throw new ModelException(
                        "Responses 工具参数不是合法 JSON。", error, modelName, providerId);
            }
        }

        private String toolId(JsonNode item) {
            var callId = text(item.path("call_id"));
            return callId.isBlank() ? text(item.path("id")) : callId;
        }

        private String finishReason(String status) {
            return switch (status) {
                case "completed" -> "stop";
                case "incomplete" -> "length";
                case "failed" -> "error";
                default -> status;
            };
        }

        private ModelException failure(JsonNode event) {
            var error = event.path("error");
            if (error.isMissingNode() || error.isNull()) {
                error = event.path("response").path("error");
            }
            var message = text(error.path("message"));
            if (message.isBlank()) {
                message = event.toString();
            }
            return new ModelException(
                    "Responses 流返回错误：" + message, modelName, providerId);
        }
    }

    private static final class ToolState {

        private final String id;

        private final String name;

        private boolean streamed;

        private ToolState(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
