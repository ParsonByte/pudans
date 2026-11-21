package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class GeminiGatewayCodec implements AiResponseEncoder {

    private static final String PROTOCOL = "Gemini";

    private static final String ROLE_MODEL = "model";

    private final ObjectMapper mapper;

    public GeminiGatewayCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.GEMINI;
    }

    public AiChatIr parse(String body, @Nullable String model, boolean stream) {
        var root = AiProtocolJson.readObject(mapper, body, PROTOCOL);
        var messages = new ArrayList<AiChatMessage>();
        var system = systemInstruction(root);
        if (system != null && !system.isEmpty()) {
            messages.add(new AiChatMessage("system", List.of(new AiContentPart.Text(system, null))));
        }
        var contents = field(root, "contents");
        if (contents == null || !contents.isArray()) {
            throw new AiProtocolException("Gemini 请求缺少 contents 数组");
        }
        for (var content : contents) {
            messages.add(parseContent(content));
        }
        return new AiChatIr(messages, parseTools(field(root, "tools")), params(root, model), stream);
    }

    @Override
    public String encode(AiChatResponse response, String model) {
        var root = mapper.createObjectNode();
        var candidate = root.putArray("candidates").addObject();
        candidate.put("index", 0);
        writeCandidate(candidate, response.message(), finishReason(response.finishReason()));
        root.put("modelVersion", model != null ? model : Objects.toString(response.model(), ""));
        var usage = usage(response.usage());
        if (usage != null) {
            root.set("usageMetadata", usage);
        }
        return AiProtocolJson.write(mapper, root, PROTOCOL);
    }

    @Override
    public AiStreamEncoder stream(String responseId, String model, AiTokenUsage usage) {
        return new StreamEncoder(Objects.toString(model, ""), usage);
    }

    private AiChatMessage parseContent(JsonNode content) {
        var role = AiProtocolJson.text(content, "role");
        var parts = field(content, "parts");
        var parsed = new ArrayList<AiContentPart>();
        if (parts != null && parts.isArray()) {
            for (var part : parts) {
                var parsedPart = parsePart(part);
                if (parsedPart != null) {
                    parsed.add(parsedPart);
                }
            }
        }
        var normalized = ROLE_MODEL.equals(role) ? "assistant" : "user";
        return new AiChatMessage(normalized, parsed);
    }

    private @Nullable AiContentPart parsePart(JsonNode part) {
        var text = AiProtocolJson.text(part, "text");
        if (text != null) {
            return Boolean.TRUE.equals(AiProtocolJson.booleanValue(part, "thought"))
                    ? new AiContentPart.Thinking(text, null, null)
                    : new AiContentPart.Text(text, null);
        }
        var inline = field(part, "inlineData");
        if (inline != null && inline.isObject()) {
            var mediaType = AiProtocolJson.text(inline, "mimeType");
            var data = AiProtocolJson.text(inline, "data");
            return mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("image/")
                    ? new AiContentPart.Image(mediaType, data, null, null, null)
                    : new AiContentPart.File(mediaType, data, null, null, null, null);
        }
        var file = field(part, "fileData");
        if (file != null && file.isObject()) {
            return new AiContentPart.File(
                    AiProtocolJson.text(file, "mimeType"),
                    null,
                    AiProtocolJson.text(file, "fileUri"),
                    null,
                    null,
                    null);
        }
        var call = field(part, "functionCall");
        if (call != null && call.isObject()) {
            return new AiContentPart.ToolCall(
                    null, AiProtocolJson.text(call, "name"), AiProtocolJson.value(call, "args"), null);
        }
        var response = field(part, "functionResponse");
        if (response != null && response.isObject()) {
            return new AiContentPart.ToolResult(null, AiProtocolJson.value(response, "response"), null, null);
        }
        var type = AiProtocolJson.text(part, "type");
        return type == null ? null : new AiContentPart.Custom(type, part);
    }

    private @Nullable String systemInstruction(ObjectNode root) {
        var node = field(root, "systemInstruction");
        if (node == null || node.isNull()) {
            return null;
        }
        var parts = node.isArray() ? node : field(node, "parts");
        if (parts == null || !parts.isArray()) {
            return null;
        }
        var joined = new StringBuilder();
        for (var part : parts) {
            var text = AiProtocolJson.text(part, "text");
            if (text != null && !text.isEmpty()) {
                if (!joined.isEmpty()) {
                    joined.append('\n');
                }
                joined.append(text);
            }
        }
        return joined.toString();
    }

    private AiRequestParams params(ObjectNode root, @Nullable String model) {
        var config = field(root, "generationConfig");
        Double temperature = null;
        Double topP = null;
        Integer topK = null;
        Integer maxTokens = null;
        var stop = new ArrayList<String>();
        var extra = new LinkedHashMap<String, JsonNode>();
        if (config != null && config.isObject()) {
            temperature = AiProtocolJson.decimal(config, "temperature");
            topP = firstDecimal(config, "topP", "top_p");
            topK = firstInteger(config, "topK", "top_k");
            maxTokens = firstInteger(config, "maxOutputTokens", "max_output_tokens");
            var stopNode = field(config, "stopSequences");
            if (stopNode != null && stopNode.isArray()) {
                for (var item : stopNode) {
                    if (item.isTextual()) {
                        stop.add(item.textValue());
                    }
                }
            }
            var mimeType = AiProtocolJson.text(config, "responseMimeType");
            if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("json")) {
                extra.put("response_format", mapper.createObjectNode().put("type", "json_object"));
            }
        }
        return new AiRequestParams(model, temperature, maxTokens, topP, topK, stop, null, extra);
    }

    private List<AiTool> parseTools(@Nullable JsonNode toolsNode) {
        if (toolsNode == null || !toolsNode.isArray()) {
            return List.of();
        }
        var tools = new ArrayList<AiTool>();
        for (var tool : toolsNode) {
            var declarations = field(tool, "functionDeclarations");
            if (declarations == null || !declarations.isArray()) {
                continue;
            }
            for (var declaration : declarations) {
                var name = AiProtocolJson.text(declaration, "name");
                if (name == null) {
                    throw new AiProtocolException("Gemini 工具缺少 name");
                }
                tools.add(new AiTool(
                        name,
                        AiProtocolJson.text(declaration, "description"),
                        AiProtocolJson.value(declaration, "parameters"),
                        null));
            }
        }
        return tools;
    }

    private void writeCandidate(ObjectNode candidate, @Nullable AiChatMessage message, String finishReason) {
        var content = candidate.putObject("content");
        content.put("role", ROLE_MODEL);
        var parts = content.putArray("parts");
        if (message != null) {
            for (var part : message.parts()) {
                if (part instanceof AiContentPart.Text text) {
                    parts.addObject().put("text", text.text());
                } else if (part instanceof AiContentPart.Thinking thinking) {
                    var node = parts.addObject();
                    node.put("text", thinking.text());
                    node.put("thought", true);
                } else if (part instanceof AiContentPart.ToolCall call) {
                    writeFunctionCall(parts.addObject(), call.name(), call.arguments());
                }
            }
        }
        candidate.put("finishReason", finishReason);
    }

    private void writeFunctionCall(ObjectNode node, @Nullable String name, @Nullable JsonNode arguments) {
        var function = node.putObject("functionCall");
        if (name != null) {
            function.put("name", name);
        }
        function.set("args", arguments == null ? mapper.createObjectNode() : arguments);
    }

    private @Nullable ObjectNode usage(@Nullable AiTokenUsage usage) {
        if (usage == null) {
            return null;
        }
        var input = usage.inputTokens() == null ? 0 : usage.inputTokens();
        var output = usage.outputTokens() == null ? 0 : usage.outputTokens();
        var node = mapper.createObjectNode();
        node.put("promptTokenCount", input);
        node.put("candidatesTokenCount", output);
        node.put("totalTokenCount", input + output);
        if (usage.cacheReadTokens() != null) {
            node.put("cachedContentTokenCount", usage.cacheReadTokens());
        }
        return node;
    }

    private static String finishReason(@Nullable String finishReason) {
        return "length".equals(finishReason) ? "MAX_TOKENS" : "STOP";
    }

    private JsonNode arguments(@Nullable String arguments) {
        if (arguments == null) {
            return mapper.createObjectNode();
        }
        try {
            var node = mapper.readTree(arguments);
            return node != null && node.isObject() ? node : mapper.createObjectNode();
        } catch (JsonProcessingException error) {
            return mapper.createObjectNode();
        }
    }

    private String data(ObjectNode node) {
        return "data: " + AiProtocolJson.write(mapper, node, PROTOCOL) + "\n\n";
    }

    private static @Nullable JsonNode field(JsonNode node, String name) {
        var direct = node.get(name);
        if (direct != null) {
            return direct;
        }
        return node.get(snakeCase(name));
    }

    private static String snakeCase(String name) {
        var builder = new StringBuilder(name.length() + 4);
        for (var index = 0; index < name.length(); index++) {
            var character = name.charAt(index);
            if (Character.isUpperCase(character)) {
                builder.append('_').append(Character.toLowerCase(character));
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static @Nullable Double firstDecimal(JsonNode node, String first, String second) {
        var value = AiProtocolJson.decimal(node, first);
        return value != null ? value : AiProtocolJson.decimal(node, second);
    }

    private static @Nullable Integer firstInteger(JsonNode node, String first, String second) {
        var value = AiProtocolJson.integer(node, first);
        return value != null ? value : AiProtocolJson.integer(node, second);
    }

    private final class StreamEncoder implements AiStreamEncoder {

        private final String model;

        private final AiTokenUsage initialUsage;

        private StreamEncoder(String model, AiTokenUsage initialUsage) {
            this.model = model;
            this.initialUsage = initialUsage;
        }

        @Override
        public String start() {
            var root = mapper.createObjectNode();
            var candidate = root.putArray("candidates").addObject();
            candidate.put("index", 0);
            var content = candidate.putObject("content");
            content.put("role", ROLE_MODEL);
            content.putArray("parts").addObject().put("text", "");
            root.put("modelVersion", model);
            return data(root);
        }

        @Override
        public String event(AiStreamEvent event) {
            if (event == null) {
                return "";
            }
            var parts = mapper.createArrayNode();
            appendParts(parts, event);
            if (parts.isEmpty()) {
                return "";
            }
            var root = mapper.createObjectNode();
            var candidate = root.putArray("candidates").addObject();
            candidate.put("index", 0);
            var content = candidate.putObject("content");
            content.put("role", ROLE_MODEL);
            content.set("parts", parts);
            root.put("modelVersion", model);
            return data(root);
        }

        @Override
        public String finish(AiStreamEvent event) {
            var root = mapper.createObjectNode();
            var candidate = root.putArray("candidates").addObject();
            candidate.put("index", 0);
            candidate.put("finishReason", finishReason(event == null ? null : event.finishReason()));
            var reported = event == null || event.usage() == null ? initialUsage : event.usage();
            var usage = usage(reported);
            if (usage != null) {
                root.set("usageMetadata", usage);
            }
            root.put("modelVersion", model);
            return data(root);
        }

        private void appendParts(ArrayNode parts, AiStreamEvent event) {
            if (event.content() != null) {
                parts.addObject().put("text", event.content());
            }
            if (event.reasoning() != null) {
                var node = parts.addObject();
                node.put("text", event.reasoning());
                node.put("thought", true);
            }
            var toolCall = event.toolCall();
            if (toolCall != null) {
                writeFunctionCall(parts.addObject(), toolCall.name(), arguments(toolCall.arguments()));
            }
        }
    }
}