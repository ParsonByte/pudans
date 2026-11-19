package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class OpenAiChatCodec implements AiProtocolCodec {

    private static final String PROTOCOL = "OpenAI Chat";

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "model",
            "messages",
            "tools",
            "temperature",
            "top_p",
            "top_k",
            "max_tokens",
            "max_completion_tokens",
            "stop",
            "stream",
            "tool_choice");

    private final ObjectMapper mapper;

    public OpenAiChatCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.OPENAI_CHAT;
    }

    @Override
    public AiChatIr parse(String body) {
        var root = AiProtocolJson.readObject(mapper, body, PROTOCOL);
        var messagesNode = root.get("messages");
        if (messagesNode == null || !messagesNode.isArray()) {
            throw new AiProtocolException("OpenAI Chat 请求缺少 messages 数组");
        }
        var messages = new ArrayList<AiChatMessage>();
        for (var messageNode : messagesNode) {
            messages.add(parseMessage(messageNode));
        }
        var params = new AiRequestParams(
                AiProtocolJson.text(root, "model"),
                AiProtocolJson.decimal(root, "temperature"),
                maxTokens(root),
                AiProtocolJson.decimal(root, "top_p"),
                AiProtocolJson.integer(root, "top_k"),
                stop(root.get("stop")),
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
        var messages = root.putArray("messages");
        for (var message : ir.messages()) {
            writeMessage(messages, message);
        }
        if (!ir.tools().isEmpty()) {
            writeTools(root.putArray("tools"), ir.tools());
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
        if (params.maxTokens() != null) {
            root.put("max_tokens", params.maxTokens());
        }
        if (!params.stop().isEmpty()) {
            var stop = root.putArray("stop");
            params.stop().forEach(stop::add);
        }
        if (params.toolChoice() != null) {
            root.set("tool_choice", params.toolChoice());
        }
        root.put("stream", ir.stream());
        return AiProtocolJson.write(mapper, root, PROTOCOL);
    }

    private AiChatMessage parseMessage(JsonNode messageNode) {
        if (messageNode == null || !messageNode.isObject()) {
            throw new AiProtocolException("OpenAI Chat 消息必须是 JSON 对象");
        }
        var role = AiProtocolJson.text(messageNode, "role");
        if (role == null) {
            throw new AiProtocolException("OpenAI Chat 消息缺少 role");
        }
        if ("tool".equals(role)) {
            return new AiChatMessage(
                    "user",
                    List.of(new AiContentPart.ToolResult(
                            AiProtocolJson.text(messageNode, "tool_call_id"),
                            AiProtocolJson.value(messageNode, "content"),
                            null,
                            AiProtocolJson.cacheControl(messageNode))));
        }
        var parts = new ArrayList<AiContentPart>();
        var reasoning = AiProtocolJson.text(messageNode, "reasoning_content");
        if (reasoning != null && !reasoning.isEmpty()) {
            parts.add(new AiContentPart.Thinking(reasoning, null, null));
        }
        var content = messageNode.get("content");
        if (content != null && !content.isNull()) {
            parts.addAll(parseContent(content));
        }
        var toolCalls = messageNode.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (var toolCall : toolCalls) {
                parts.add(parseToolCall(toolCall));
            }
        }
        return new AiChatMessage(role, parts);
    }

    private List<AiContentPart> parseContent(JsonNode content) {
        if (content.isTextual()) {
            return List.of(new AiContentPart.Text(content.textValue(), null));
        }
        if (!content.isArray()) {
            throw new AiProtocolException("OpenAI Chat 消息 content 必须是字符串或数组");
        }
        var parts = new ArrayList<AiContentPart>();
        for (var item : content) {
            if (!item.isObject()) {
                throw new AiProtocolException("OpenAI Chat 内容 block 必须是 JSON 对象");
            }
            var type = AiProtocolJson.text(item, "type");
            if ("text".equals(type)) {
                parts.add(new AiContentPart.Text(
                        AiProtocolJson.text(item, "text"), AiProtocolJson.cacheControl(item)));
            } else if ("image_url".equals(type)) {
                parts.add(parseImage(item));
            } else if ("file".equals(type)) {
                parts.add(parseFile(item));
            } else {
                parts.add(new AiContentPart.Custom(type, item));
            }
        }
        return parts;
    }

    private AiContentPart parseImage(JsonNode item) {
        var imageUrl = item.get("image_url");
        var cacheControl = AiProtocolJson.cacheControl(item);
        if (imageUrl != null && imageUrl.isObject()) {
            return new AiContentPart.Image(
                    null,
                    null,
                    AiProtocolJson.text(imageUrl, "url"),
                    AiProtocolJson.text(imageUrl, "detail"),
                    cacheControl);
        }
        if (imageUrl != null && imageUrl.isTextual()) {
            return new AiContentPart.Image(null, null, imageUrl.textValue(), null, cacheControl);
        }
        return new AiContentPart.Custom("image_url", item);
    }

    private AiContentPart parseFile(JsonNode item) {
        var file = item.get("file");
        if (file == null || !file.isObject()) {
            return new AiContentPart.Custom("file", item);
        }
        return new AiContentPart.File(
                null,
                AiProtocolJson.text(file, "file_data"),
                null,
                AiProtocolJson.text(file, "filename"),
                AiProtocolJson.text(file, "file_id"),
                AiProtocolJson.cacheControl(item));
    }

    private AiContentPart parseToolCall(JsonNode toolCallNode) {
        if (!toolCallNode.isObject()) {
            throw new AiProtocolException("OpenAI Chat tool_calls 元素必须是 JSON 对象");
        }
        var function = toolCallNode.get("function");
        if (function == null || !function.isObject()) {
            return new AiContentPart.Custom("tool_call", toolCallNode);
        }
        return new AiContentPart.ToolCall(
                AiProtocolJson.text(toolCallNode, "id"),
                AiProtocolJson.text(function, "name"),
                parseArguments(AiProtocolJson.text(function, "arguments")),
                AiProtocolJson.cacheControl(toolCallNode));
    }

    private JsonNode parseArguments(String arguments) {
        if (arguments == null) {
            return null;
        }
        try {
            return mapper.readTree(arguments);
        } catch (JsonProcessingException e) {
            return mapper.getNodeFactory().textNode(arguments);
        }
    }

    private List<AiTool> parseTools(JsonNode toolsNode) {
        if (toolsNode == null || toolsNode.isNull()) {
            return List.of();
        }
        if (!toolsNode.isArray()) {
            throw new AiProtocolException("OpenAI Chat tools 必须是数组");
        }
        var tools = new ArrayList<AiTool>();
        for (var toolNode : toolsNode) {
            if (!toolNode.isObject()) {
                throw new AiProtocolException("OpenAI Chat 工具必须是 JSON 对象");
            }
            var type = AiProtocolJson.text(toolNode, "type");
            var function = toolNode.get("function");
            if (type != null && !"function".equals(type)) {
                continue;
            }
            if (function == null || !function.isObject()) {
                continue;
            }
            var name = AiProtocolJson.text(function, "name");
            if (name == null) {
                throw new AiProtocolException("OpenAI Chat 工具缺少 function.name");
            }
            tools.add(new AiTool(
                    name,
                    AiProtocolJson.text(function, "description"),
                    AiProtocolJson.value(function, "parameters"),
                    AiProtocolJson.cacheControl(toolNode)));
        }
        return tools;
    }

    private void writeMessage(ArrayNode messages, AiChatMessage message) {
        var contentParts = new ArrayList<AiContentPart>();
        var toolCalls = new ArrayList<AiContentPart.ToolCall>();
        var toolResults = new ArrayList<AiContentPart.ToolResult>();
        var reasoning = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.ToolResult result) {
                toolResults.add(result);
            } else if (part instanceof AiContentPart.ToolCall call) {
                toolCalls.add(call);
            } else if (part instanceof AiContentPart.Thinking thinking) {
                if (!reasoning.isEmpty()) {
                    reasoning.append('\n');
                }
                reasoning.append(thinking.text());
            } else {
                contentParts.add(part);
            }
        }
        for (var result : toolResults) {
            var toolNode = messages.addObject();
            toolNode.put("role", "tool");
            if (result.toolCallId() != null) {
                toolNode.put("tool_call_id", result.toolCallId());
            }
            if (result.content() != null) {
                toolNode.set("content", result.content());
            }
            AiProtocolJson.putCacheControl(toolNode, result.cacheControl());
        }
        if (contentParts.isEmpty() && toolCalls.isEmpty() && reasoning.isEmpty()) {
            return;
        }
        var messageNode = messages.addObject();
        messageNode.put("role", message.role());
        if (!reasoning.isEmpty()) {
            messageNode.put("reasoning_content", reasoning.toString());
        }
        writeContent(messageNode, contentParts);
        if (!toolCalls.isEmpty()) {
            writeToolCalls(messageNode, toolCalls);
        }
    }

    private void writeContent(ObjectNode messageNode, List<AiContentPart> parts) {
        if (parts.isEmpty()) {
            return;
        }
        if (parts.size() == 1
                && parts.get(0) instanceof AiContentPart.Text text
                && text.cacheControl() == null) {
            messageNode.put("content", text.text());
            return;
        }
        var content = messageNode.putArray("content");
        for (var part : parts) {
            if (part instanceof AiContentPart.Text text) {
                var block = content.addObject();
                block.put("type", "text");
                block.put("text", text.text());
                AiProtocolJson.putCacheControl(block, text.cacheControl());
            } else if (part instanceof AiContentPart.Image image) {
                var block = content.addObject();
                block.put("type", "image_url");
                var imageUrlNode = block.putObject("image_url");
                imageUrlNode.put("url", imageUrl(image));
                if (image.detail() != null) {
                    imageUrlNode.put("detail", image.detail());
                }
                AiProtocolJson.putCacheControl(block, image.cacheControl());
            } else if (part instanceof AiContentPart.File file) {
                var block = content.addObject();
                block.put("type", "file");
                var fileNode = block.putObject("file");
                if (file.data() != null) {
                    var data = file.data();
                    if (!data.startsWith("data:")) {
                        var mediaType = file.mediaType() == null
                                ? "application/octet-stream"
                                : file.mediaType();
                        data = "data:" + mediaType + ";base64," + data;
                    }
                    fileNode.put("file_data", data);
                }
                if (file.fileId() != null) {
                    fileNode.put("file_id", file.fileId());
                }
                if (file.filename() != null) {
                    fileNode.put("filename", file.filename());
                }
                AiProtocolJson.putCacheControl(block, file.cacheControl());
            } else if (part instanceof AiContentPart.Custom custom) {
                content.add(custom.raw());
            }
        }
    }

    private void writeToolCalls(ObjectNode messageNode, List<AiContentPart.ToolCall> toolCalls) {
        var tools = messageNode.putArray("tool_calls");
        for (var call : toolCalls) {
            var block = tools.addObject();
            if (call.id() != null) {
                block.put("id", call.id());
            }
            block.put("type", "function");
            var function = block.putObject("function");
            if (call.name() != null) {
                function.put("name", call.name());
            }
            function.put("arguments", arguments(call.arguments()));
            AiProtocolJson.putCacheControl(block, call.cacheControl());
        }
    }

    private void writeTools(ArrayNode toolsNode, List<AiTool> tools) {
        for (var tool : tools) {
            var toolNode = toolsNode.addObject();
            toolNode.put("type", "function");
            var function = toolNode.putObject("function");
            function.put("name", tool.name());
            if (tool.description() != null) {
                function.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                function.set("parameters", tool.parameters());
            }
            AiProtocolJson.putCacheControl(toolNode, tool.cacheControl());
        }
    }

    private String arguments(JsonNode arguments) {
        if (arguments == null) {
            return "{}";
        }
        if (arguments.isTextual()) {
            return arguments.textValue();
        }
        return AiProtocolJson.write(mapper, arguments, PROTOCOL);
    }

    private static String imageUrl(AiContentPart.Image image) {
        if (image.url() != null) {
            return image.url();
        }
        if (image.data() == null) {
            return "";
        }
        var mediaType = image.mediaType() == null ? "application/octet-stream" : image.mediaType();
        return "data:" + mediaType + ";base64," + image.data();
    }

    private static Integer maxTokens(JsonNode root) {
        var value = AiProtocolJson.integer(root, "max_completion_tokens");
        return value != null ? value : AiProtocolJson.integer(root, "max_tokens");
    }

    private static List<String> stop(JsonNode stopNode) {
        if (stopNode == null || stopNode.isNull()) {
            return List.of();
        }
        if (stopNode.isTextual()) {
            return List.of(stopNode.textValue());
        }
        if (!stopNode.isArray()) {
            throw new AiProtocolException("OpenAI Chat stop 必须是字符串或数组");
        }
        var stops = new ArrayList<String>();
        for (var stop : stopNode) {
            if (!stop.isTextual()) {
                throw new AiProtocolException("OpenAI Chat stop 元素必须是字符串");
            }
            stops.add(stop.textValue());
        }
        return stops;
    }
}
