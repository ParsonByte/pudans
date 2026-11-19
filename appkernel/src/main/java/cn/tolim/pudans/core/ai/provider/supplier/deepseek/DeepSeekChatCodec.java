package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderIrSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class DeepSeekChatCodec {

    private static final String JSON_TYPE = "type";

    private static final Set<String> SERVER_TOOLS =
            Set.of("deepseek_web_search", "deepseek_read_url");

    private static final Set<String> MESSAGE_FIELDS =
            Set.of("server_tool_calls", "prefix", "reasoning_content");

    private static final Set<String> RESPONSE_ONLY =
            Set.of("annotations", "title", "refusal");

    private final ObjectMapper mapper;

    DeepSeekChatCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    String serialize(AiChatIr request) {
        var params = request.requestParams();
        var root = mapper.createObjectNode();
        params.extra().forEach(root::set);
        if (params.model() != null) {
            root.put("model", params.model());
        }
        var messages = root.putArray("messages");
        for (var message : request.messages()) {
            writeMessage(messages, message);
        }
        if (!request.tools().isEmpty()) {
            writeTools(root.putArray("tools"), request.tools());
        }
        writeParams(root, params);
        root.put("stream", request.stream());
        return AiProviderJsonSupport.write(mapper, root, DeepSeekProtocol.NAME);
    }

    private void writeMessage(ArrayNode messages, AiChatMessage message) {
        var content = new ArrayList<AiContentPart>();
        var toolCalls = new ArrayList<AiContentPart.ToolCall>();
        var customFields = new ArrayList<AiContentPart.Custom>();
        var reasoning = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.ToolResult result) {
                writeToolResult(messages, result);
            } else if (part instanceof AiContentPart.ToolCall call) {
                toolCalls.add(call);
            } else if (part instanceof AiContentPart.Thinking thinking) {
                if (!reasoning.isEmpty()) {
                    reasoning.append('\n');
                }
                reasoning.append(thinking.text());
            } else if (part instanceof AiContentPart.Custom custom
                    && MESSAGE_FIELDS.contains(custom.type())) {
                customFields.add(custom);
            } else if (part instanceof AiContentPart.Custom custom
                    && RESPONSE_ONLY.contains(custom.type())) {
                continue;
            } else {
                content.add(part);
            }
        }
        if (content.isEmpty() && toolCalls.isEmpty() && customFields.isEmpty() && reasoning.isEmpty()) {
            return;
        }
        var messageNode = messages.addObject();
        messageNode.put("role", chatRole(message.role()));
        if (!reasoning.isEmpty()) {
            messageNode.put("reasoning_content", reasoning.toString());
        }
        writeContent(messageNode, content);
        if (!toolCalls.isEmpty()) {
            writeToolCalls(messageNode, toolCalls);
        }
        for (var custom : customFields) {
            messageNode.set(custom.type(), custom.raw());
        }
    }

    private void writeContent(ObjectNode messageNode, List<AiContentPart> content) {
        if (content.isEmpty()) {
            messageNode.putNull("content");
            return;
        }
        if (content.size() == 1 && content.get(0) instanceof AiContentPart.Text text) {
            messageNode.put("content", text.text());
            return;
        }
        var blocks = messageNode.putArray("content");
        for (var part : content) {
            writeBlock(blocks, part);
        }
    }

    private void writeBlock(ArrayNode blocks, AiContentPart part) {
        if (part instanceof AiContentPart.Text text) {
            var block = blocks.addObject();
            block.put(JSON_TYPE, "text");
            block.put("text", text.text());
        } else if (part instanceof AiContentPart.Image image) {
            var block = blocks.addObject();
            block.put(JSON_TYPE, "image_url");
            var imageUrl = block.putObject("image_url");
            imageUrl.put("url", imageUrl(image));
            if (image.detail() != null) {
                imageUrl.put("detail", image.detail());
            }
        } else if (part instanceof AiContentPart.File file) {
            writeFileBlock(blocks, file);
        } else if (part instanceof AiContentPart.Custom custom) {
            blocks.add(custom.raw());
        }
    }

    private void writeFileBlock(ArrayNode blocks, AiContentPart.File file) {
        var block = blocks.addObject();
        block.put(JSON_TYPE, "file");
        if (file.fileId() != null) {
            block.put("file_id", file.fileId());
            return;
        }
        if (file.data() == null) {
            throw new AiProtocolException("DeepSeek 文件块只支持 file_id 或 base64 内容");
        }
        var mediaType = file.mediaType() == null ? "application/octet-stream" : file.mediaType();
        block.put("file_data", "data:" + mediaType + ";base64," + file.data());
        if (file.filename() != null) {
            block.put("filename", file.filename());
        }
    }

    private void writeToolResult(ArrayNode messages, AiContentPart.ToolResult result) {
        var node = messages.addObject();
        node.put("role", "tool");
        if (result.toolCallId() != null) {
            node.put("tool_call_id", result.toolCallId());
        }
        if (result.content() == null || result.content().isNull()) {
            node.put("content", "");
        } else if (result.content().isTextual()) {
            node.put("content", result.content().textValue());
        } else if (result.content().isArray()) {
            node.set("content", result.content());
        } else {
            node.put("content", result.content().toString());
        }
    }

    private void writeToolCalls(ObjectNode messageNode, List<AiContentPart.ToolCall> toolCalls) {
        var array = messageNode.putArray("tool_calls");
        for (var call : toolCalls) {
            var block = array.addObject();
            if (call.id() != null) {
                block.put("id", call.id());
            }
            block.put(JSON_TYPE, "function");
            var function = block.putObject("function");
            if (call.name() != null) {
                function.put("name", call.name());
            }
            function.put("arguments",
                    AiProviderJsonSupport.argumentsText(mapper, call.arguments(), DeepSeekProtocol.NAME));
        }
    }

    private void writeTools(ArrayNode toolsNode, List<AiTool> tools) {
        for (var tool : tools) {
            if (SERVER_TOOLS.contains(tool.name())) {
                toolsNode.addObject().put(JSON_TYPE, tool.name());
                continue;
            }
            var toolNode = toolsNode.addObject();
            toolNode.put(JSON_TYPE, "function");
            var function = toolNode.putObject("function");
            function.put("name", tool.name());
            if (tool.description() != null) {
                function.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                function.set("parameters", tool.parameters());
            }
        }
    }

    private void writeParams(ObjectNode root, AiRequestParams params) {
        if (params.maxTokens() != null) {
            root.put("max_tokens", params.maxTokens());
        }
        if (params.temperature() != null) {
            root.put("temperature", params.temperature());
        }
        if (params.topP() != null) {
            root.put("top_p", params.topP());
        }
        if (!params.stop().isEmpty()) {
            var stop = root.putArray("stop");
            params.stop().forEach(stop::add);
        }
        if (params.toolChoice() != null) {
            root.set("tool_choice", params.toolChoice());
        }
    }

    private static String chatRole(String role) {
        if (AiProviderIrSupport.isSystem(role)) {
            return "system";
        }
        return "tool".equals(role) ? "user" : role;
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
}
