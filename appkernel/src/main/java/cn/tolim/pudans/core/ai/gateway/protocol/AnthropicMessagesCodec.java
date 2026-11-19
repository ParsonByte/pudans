package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AnthropicMessagesCodec implements AiProtocolCodec {

    private static final String PROTOCOL = "Anthropic";

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "model",
            "system",
            "messages",
            "tools",
            "max_tokens",
            "temperature",
            "top_p",
            "top_k",
            "stop_sequences",
            "stream",
            "tool_choice");

    private final ObjectMapper mapper;

    public AnthropicMessagesCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.ANTHROPIC;
    }

    @Override
    public AiChatIr parse(String body) {
        var root = AiProtocolJson.readObject(mapper, body, PROTOCOL);
        var messagesNode = root.get("messages");
        if (messagesNode == null || !messagesNode.isArray()) {
            throw new AiProtocolException("Anthropic 请求缺少 messages 数组");
        }
        var messages = new ArrayList<AiChatMessage>();
        var system = root.get("system");
        if (system != null && !system.isNull()) {
            messages.add(new AiChatMessage("system", parseSystem(system)));
        }
        for (var messageNode : messagesNode) {
            messages.add(parseMessage(messageNode));
        }
        var params = new AiRequestParams(
                AiProtocolJson.text(root, "model"),
                AiProtocolJson.decimal(root, "temperature"),
                AiProtocolJson.integer(root, "max_tokens"),
                AiProtocolJson.decimal(root, "top_p"),
                AiProtocolJson.integer(root, "top_k"),
                stringList(root.get("stop_sequences"), "Anthropic stop_sequences"),
                AiProtocolJson.value(root, "tool_choice"),
                AiProtocolJson.extras(root, KNOWN_FIELDS));
        return new AiChatIr(messages, parseTools(root.get("tools")), params, AiProtocolJson.bool(root, "stream"));
    }

    @Override
    public String serialize(AiChatIr ir) {
        var params = ir.requestParams();
        var root = mapper.createObjectNode();
        AiProtocolJson.putExtras(root, params.extra());
        if (params.model() != null) {
            root.put("model", params.model());
        }
        writeSystem(root, ir.messages());
        var messages = root.putArray("messages");
        for (var message : ir.messages()) {
            if (!"system".equals(message.role())) {
                writeMessage(messages, message);
            }
        }
        if (!ir.tools().isEmpty()) {
            writeTools(root.putArray("tools"), ir.tools());
        }
        if (params.maxTokens() != null) {
            root.put("max_tokens", params.maxTokens());
        }
        if (params.temperature() != null) {
            root.put("temperature", params.temperature());
        }
        if (params.topP() != null) {
            root.put("top_p", params.topP());
        }
        if (params.topK() != null) {
            root.put("top_k", params.topK());
        }
        if (!params.stop().isEmpty()) {
            var stop = root.putArray("stop_sequences");
            params.stop().forEach(stop::add);
        }
        if (params.toolChoice() != null) {
            root.set("tool_choice", params.toolChoice());
        }
        root.put("stream", ir.stream());
        return AiProtocolJson.write(mapper, root, PROTOCOL);
    }

    private List<AiContentPart> parseSystem(JsonNode system) {
        if (system.isTextual()) {
            return List.of(new AiContentPart.Text(system.textValue(), null));
        }
        if (!system.isArray()) {
            throw new AiProtocolException("Anthropic system 必须是字符串或数组");
        }
        var parts = new ArrayList<AiContentPart>();
        for (var block : system) {
            if (!block.isObject()) {
                throw new AiProtocolException("Anthropic system block 必须是 JSON 对象");
            }
            var type = AiProtocolJson.text(block, "type");
            if ("text".equals(type)) {
                parts.add(new AiContentPart.Text(
                        AiProtocolJson.text(block, "text"), AiProtocolJson.cacheControl(block)));
            } else {
                parts.add(new AiContentPart.Custom(type, block));
            }
        }
        return parts;
    }

    private AiChatMessage parseMessage(JsonNode messageNode) {
        if (messageNode == null || !messageNode.isObject()) {
            throw new AiProtocolException("Anthropic 消息必须是 JSON 对象");
        }
        var role = AiProtocolJson.text(messageNode, "role");
        if (role == null) {
            throw new AiProtocolException("Anthropic 消息缺少 role");
        }
        var parts = new ArrayList<AiContentPart>();
        var content = messageNode.get("content");
        if (content != null && !content.isNull()) {
            if (content.isTextual()) {
                parts.add(new AiContentPart.Text(content.textValue(), null));
            } else if (content.isArray()) {
                for (var block : content) {
                    parts.add(parseBlock(block));
                }
            } else {
                throw new AiProtocolException("Anthropic 消息 content 必须是字符串或数组");
            }
        }
        return new AiChatMessage(role, parts);
    }

    private AiContentPart parseBlock(JsonNode block) {
        if (!block.isObject()) {
            throw new AiProtocolException("Anthropic 内容 block 必须是 JSON 对象");
        }
        var type = AiProtocolJson.text(block, "type");
        if (type == null) {
            throw new AiProtocolException("Anthropic 内容 block 缺少 type");
        }
        var cacheControl = AiProtocolJson.cacheControl(block);
        return switch (type) {
            case "text" -> new AiContentPart.Text(AiProtocolJson.text(block, "text"), cacheControl);
            case "image" -> parseImage(block, cacheControl);
            case "document" -> parseDocument(block, cacheControl);
            case "tool_use" -> new AiContentPart.ToolCall(
                    AiProtocolJson.text(block, "id"),
                    AiProtocolJson.text(block, "name"),
                    AiProtocolJson.value(block, "input"),
                    cacheControl);
            case "tool_result" -> new AiContentPart.ToolResult(
                    AiProtocolJson.text(block, "tool_use_id"),
                    AiProtocolJson.value(block, "content"),
                    AiProtocolJson.booleanValue(block, "is_error"),
                    cacheControl);
            case "thinking" -> new AiContentPart.Thinking(
                    AiProtocolJson.text(block, "thinking"),
                    AiProtocolJson.text(block, "signature"),
                    cacheControl);
            default -> new AiContentPart.Custom(type, block);
        };
    }

    private AiContentPart parseImage(JsonNode block, JsonNode cacheControl) {
        var source = block.get("source");
        if (source == null || !source.isObject()) {
            return new AiContentPart.Custom("image", block);
        }
        var type = AiProtocolJson.text(source, "type");
        if ("base64".equals(type)) {
            return new AiContentPart.Image(
                    AiProtocolJson.text(source, "media_type"),
                    AiProtocolJson.text(source, "data"),
                    null,
                    null,
                    cacheControl);
        }
        if ("url".equals(type)) {
            return new AiContentPart.Image(
                    AiProtocolJson.text(source, "media_type"),
                    null,
                    AiProtocolJson.text(source, "url"),
                    null,
                    cacheControl);
        }
        return new AiContentPart.Custom("image", block);
    }

    private AiContentPart parseDocument(JsonNode block, JsonNode cacheControl) {
        var source = block.get("source");
        if (source == null || !source.isObject()) {
            return new AiContentPart.Custom("document", block);
        }
        var type = AiProtocolJson.text(source, "type");
        if ("text".equals(type)) {
            return new AiContentPart.Text(AiProtocolJson.text(source, "data"), cacheControl);
        }
        if ("base64".equals(type)) {
            return new AiContentPart.File(
                    AiProtocolJson.text(source, "media_type"),
                    AiProtocolJson.text(source, "data"),
                    null,
                    null,
                    null,
                    cacheControl);
        }
        if ("url".equals(type)) {
            return new AiContentPart.File(
                    AiProtocolJson.text(source, "media_type"),
                    null,
                    AiProtocolJson.text(source, "url"),
                    null,
                    null,
                    cacheControl);
        }
        return new AiContentPart.Custom("document", block);
    }

    private List<AiTool> parseTools(JsonNode toolsNode) {
        if (toolsNode == null || toolsNode.isNull()) {
            return List.of();
        }
        if (!toolsNode.isArray()) {
            throw new AiProtocolException("Anthropic tools 必须是数组");
        }
        var tools = new ArrayList<AiTool>();
        for (var toolNode : toolsNode) {
            if (!toolNode.isObject()) {
                throw new AiProtocolException("Anthropic 工具必须是 JSON 对象");
            }
            var name = AiProtocolJson.text(toolNode, "name");
            if (name == null) {
                throw new AiProtocolException("Anthropic 工具缺少 name");
            }
            tools.add(new AiTool(
                    name,
                    AiProtocolJson.text(toolNode, "description"),
                    AiProtocolJson.value(toolNode, "input_schema"),
                    AiProtocolJson.cacheControl(toolNode)));
        }
        return tools;
    }

    private void writeSystem(ObjectNode root, List<AiChatMessage> messages) {
        var parts = new ArrayList<AiContentPart>();
        for (var message : messages) {
            if ("system".equals(message.role())) {
                parts.addAll(message.parts());
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        if (parts.size() == 1
                && parts.get(0) instanceof AiContentPart.Text text
                && text.cacheControl() == null) {
            root.put("system", text.text());
            return;
        }
        var system = root.putArray("system");
        for (var part : parts) {
            writeBlock(system, part);
        }
    }

    private void writeMessage(ArrayNode messages, AiChatMessage message) {
        var messageNode = messages.addObject();
        messageNode.put("role", message.role());
        if (message.parts().size() == 1
                && message.parts().get(0) instanceof AiContentPart.Text text
                && text.cacheControl() == null) {
            messageNode.put("content", text.text());
            return;
        }
        var content = messageNode.putArray("content");
        for (var part : message.parts()) {
            writeBlock(content, part);
        }
    }

    private void writeBlock(ArrayNode content, AiContentPart part) {
        if (part instanceof AiContentPart.Text text) {
            var block = content.addObject();
            block.put("type", "text");
            block.put("text", text.text());
            AiProtocolJson.putCacheControl(block, text.cacheControl());
        } else if (part instanceof AiContentPart.Image image) {
            var block = content.addObject();
            block.put("type", "image");
            block.set("source", source(image.mediaType(), image.data(), image.url()));
            AiProtocolJson.putCacheControl(block, image.cacheControl());
        } else if (part instanceof AiContentPart.File file) {
            var block = content.addObject();
            block.put("type", "document");
            block.set("source", source(file.mediaType(), file.data(), file.url()));
            AiProtocolJson.putCacheControl(block, file.cacheControl());
        } else if (part instanceof AiContentPart.ToolCall call) {
            var block = content.addObject();
            block.put("type", "tool_use");
            if (call.id() != null) {
                block.put("id", call.id());
            }
            if (call.name() != null) {
                block.put("name", call.name());
            }
            if (call.arguments() != null) {
                block.set("input", call.arguments());
            }
            AiProtocolJson.putCacheControl(block, call.cacheControl());
        } else if (part instanceof AiContentPart.ToolResult result) {
            var block = content.addObject();
            block.put("type", "tool_result");
            if (result.toolCallId() != null) {
                block.put("tool_use_id", result.toolCallId());
            }
            if (result.content() != null) {
                block.set("content", result.content());
            }
            if (result.error() != null) {
                block.put("is_error", result.error());
            }
            AiProtocolJson.putCacheControl(block, result.cacheControl());
        } else if (part instanceof AiContentPart.Thinking thinking) {
            var block = content.addObject();
            block.put("type", "thinking");
            block.put("thinking", thinking.text());
            if (thinking.signature() != null) {
                block.put("signature", thinking.signature());
            }
            AiProtocolJson.putCacheControl(block, thinking.cacheControl());
        } else if (part instanceof AiContentPart.Custom custom) {
            content.add(custom.raw());
        }
    }

    private void writeTools(ArrayNode toolsNode, List<AiTool> tools) {
        for (var tool : tools) {
            var toolNode = toolsNode.addObject();
            toolNode.put("name", tool.name());
            if (tool.description() != null) {
                toolNode.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                toolNode.put("input_schema", tool.parameters());
            }
            AiProtocolJson.putCacheControl(toolNode, tool.cacheControl());
        }
    }

    private ObjectNode source(String mediaType, String data, String url) {
        var source = mapper.createObjectNode();
        if (data != null) {
            source.put("type", "base64");
            if (mediaType != null) {
                source.put("media_type", mediaType);
            }
            source.put("data", data);
            return source;
        }
        source.put("type", "url");
        if (url != null) {
            source.put("url", url);
        }
        return source;
    }

    private static List<String> stringList(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new AiProtocolException(field + " 必须是数组");
        }
        var values = new ArrayList<String>();
        for (var value : node) {
            if (!value.isTextual()) {
                throw new AiProtocolException(field + " 元素必须是字符串");
            }
            values.add(value.textValue());
        }
        return values;
    }
}
