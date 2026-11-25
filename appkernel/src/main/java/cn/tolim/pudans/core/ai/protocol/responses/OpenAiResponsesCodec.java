package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.formatter.JsonSchema;
import io.agentscope.core.message.AudioBlock;
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
import io.agentscope.core.message.VideoBlock;
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

public class OpenAiResponsesCodec {

    private static final String JSON_SCHEMA = "json_schema";

    private static final String FUNCTION_CALL = "function_call";

    private static final String OUTPUT_TEXT = "output_text";

    private static final String PROVIDER_OUTPUT_ITEMS = "providerOutputItems";

    private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() {};

    private final ResponsesProfile profile;

    public OpenAiResponsesCodec(ResponsesProfile profile) {
        this.profile = profile;
    }

    public Map<String, Object> request(
            String modelId,
            List<Msg> messages,
            List<ToolSchema> tools,
            GenerateOptions options,
            Integer fallbackMaxTokens) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("input", inputItems(messages));
        var instructions = systemText(messages);
        if (!instructions.isBlank()) {
            body.put("instructions", instructions);
        }
        put(
                body,
                "max_output_tokens",
                options == null ? fallbackMaxTokens : maxOutputTokens(options, fallbackMaxTokens));
        if (options != null) {
            var temperature = options.getTemperature();
            if (temperature != null) {
                profile.validateTemperature(temperature);
                body.put("temperature", temperature);
            }
            var topP = options.getTopP();
            if (topP != null) {
                profile.validateTopP(topP);
                body.put("top_p", topP);
            }
            if (options.getReasoningEffort() != null) {
                put(body, "reasoning", profile.reasoning(options.getReasoningEffort()));
            }
            var parallel = options.getParallelToolCalls();
            if (parallel != null && !profile.supportsParallelToolCalls()) {
                throw new IllegalArgumentException("当前 Responses 协议不支持 parallel_tool_calls。");
            }
            put(body, "parallel_tool_calls", parallel);
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools(tools));
        }
        if (options != null && options.getToolChoice() != null) {
            var choice = options.getToolChoice();
            var custom = profile.toolChoice(choice);
            body.put("tool_choice", custom != null ? custom : standardToolChoice(choice));
        }
        applyResponseFormat(body, options);
        var store = profile.store();
        if (store != null) {
            body.put("store", store);
        }
        profile.customizeBody(body);
        profile.unsupportedBodyFields().forEach(body::remove);
        body.put("stream", true);
        return Map.copyOf(body);
    }

    public StreamDecoder streamDecoder(String modelName, String providerId) {
        return new StreamDecoder(modelName, providerId);
    }

    private List<Map<String, Object>> inputItems(List<Msg> messages) {
        var items = new ArrayList<Map<String, Object>>();
        if (messages == null) {
            return List.of();
        }
        for (var message : messages) {
            switch (message.getRole()) {
                case USER -> items.add(userItem(message.getContent()));
                case ASSISTANT -> items.addAll(assistantItems(message));
                case TOOL -> items.addAll(toolResultItems(message.getContent()));
                case SYSTEM -> {}
            }
        }
        return List.copyOf(items);
    }

    private Map<String, Object> userItem(List<ContentBlock> blocks) {
        var content = new ArrayList<Map<String, Object>>();
        for (var block : blocks) {
            if (block instanceof TextBlock text) {
                content.add(Map.of("type", "input_text", "text", text.getText()));
            } else {
                content.add(mediaItem(block));
            }
        }
        return Map.of("role", "user", "content", List.copyOf(content));
    }

    private List<Map<String, Object>> assistantItems(Msg message) {
        var items = new ArrayList<Map<String, Object>>();
        items.addAll(replayedItems(message.getMetadata()));
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

    private List<Map<String, Object>> replayedItems(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        var value = metadata.get(PROVIDER_OUTPUT_ITEMS);
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var allowed = profile.replayedItemTypes();
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

    private Map<String, Object> mediaItem(ContentBlock block) {
        var source = source(block);
        if (source == null) {
            throw unsupported("Responses 消息包含不支持的媒体块。");
        }
        var mediaType = mediaType(source);
        var mediaData = mediaData(source);
        var custom = profile.mediaItem(mediaType, mediaData);
        if (custom != null) {
            return custom;
        }
        if (mediaType.startsWith("image/")) {
            return Map.of("type", "input_image", "image_url", mediaData);
        }
        return Map.of("type", "input_file", "filename", "input", "file_data", mediaData);
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
        if (block instanceof VideoBlock video) {
            return video.getSource();
        }
        if (block instanceof AudioBlock audio) {
            return audio.getSource();
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

    private static Object standardToolChoice(ToolChoice choice) {
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
        return ResponsesProfile.unsupportedMedia(message);
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
                var type = profile.eventType(event);
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
                if (!profile.providerOutputItemTypes().contains(type)) {
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
            if (status == null) {
                return null;
            }
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
