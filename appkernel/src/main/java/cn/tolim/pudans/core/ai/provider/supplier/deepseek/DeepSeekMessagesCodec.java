package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderIrSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class DeepSeekMessagesCodec {

    private static final String JSON_TYPE = "type";

    private static final String SERVER_TOOL_WEB_SEARCH = "deepseek_web_search";

    private static final String SERVER_TOOL_READ_URL = "deepseek_read_url";

    private static final Set<String> SERVER_TOOLS = Set.of(SERVER_TOOL_WEB_SEARCH, SERVER_TOOL_READ_URL);

    private final ObjectMapper mapper;

    DeepSeekMessagesCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    String serialize(AiChatIr request) {
        var params = request.requestParams();
        var root = mapper.createObjectNode();
        params.extra().forEach(root::set);
        if (params.model() != null) {
            root.put("model", params.model());
        }
        writeSystem(root, request.messages());
        var messages = root.putArray("messages");
        for (var message : request.messages()) {
            if (!AiProviderIrSupport.isSystem(message.role())) {
                writeMessage(messages, message);
            }
        }
        if (!request.tools().isEmpty()) {
            writeTools(root.putArray("tools"), request.tools());
        }
        writeParams(root, params);
        root.put("stream", request.stream());
        return AiProviderJsonSupport.write(mapper, root, DeepSeekProtocol.NAME);
    }

    private void writeSystem(ObjectNode root, List<AiChatMessage> messages) {
        var sections = messages.stream()
                .filter(message -> AiProviderIrSupport.isSystem(message.role()))
                .map(message -> AiProviderIrSupport.text(message.parts()))
                .filter(text -> !text.isEmpty())
                .toList();
        if (!sections.isEmpty()) {
            root.put("system", String.join("\n\n", sections));
        }
    }

    private void writeMessage(ArrayNode messages, AiChatMessage message) {
        var messageNode = messages.addObject();
        messageNode.put("role", message.role());
        if (message.parts().size() == 1 && message.parts().get(0) instanceof AiContentPart.Text text) {
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
            block.put(JSON_TYPE, "text");
            block.put("text", text.text());
        } else if (part instanceof AiContentPart.Image image) {
            var block = content.addObject();
            block.put(JSON_TYPE, "image");
            block.set("source", source(image.mediaType(), image.data(), image.url(), null));
        } else if (part instanceof AiContentPart.File file) {
            if (file.fileId() == null) {
                throw new AiProtocolException("DeepSeek 文档块只支持 file_id 来源");
            }
            var block = content.addObject();
            block.put(JSON_TYPE, isImage(file.mediaType()) ? "image" : "document");
            block.set("source", source(file.mediaType(), file.data(), file.url(), file.fileId()));
        } else if (part instanceof AiContentPart.ToolCall call) {
            var block = content.addObject();
            block.put(JSON_TYPE, "tool_use");
            if (call.id() != null) {
                block.put("id", call.id());
            }
            if (call.name() != null) {
                block.put("name", call.name());
            }
            block.set("input", input(call.arguments()));
        } else if (part instanceof AiContentPart.ToolResult result) {
            var block = content.addObject();
            block.put(JSON_TYPE, "tool_result");
            if (result.toolCallId() != null) {
                block.put("tool_use_id", result.toolCallId());
            }
            if (result.content() != null) {
                block.set("content", result.content());
            }
            if (result.error() != null) {
                block.put("is_error", result.error());
            }
        } else if (part instanceof AiContentPart.Thinking thinking) {
            var block = content.addObject();
            block.put(JSON_TYPE, "thinking");
            block.put("thinking", thinking.text());
            if (thinking.signature() != null) {
                block.put("signature", thinking.signature());
            }
        } else if (part instanceof AiContentPart.Custom custom) {
            content.add(custom.raw());
        }
    }

    private ObjectNode source(String mediaType, String data, String url, String fileId) {
        var source = mapper.createObjectNode();
        if (fileId != null) {
            source.put(JSON_TYPE, "file");
            source.put("file_id", fileId);
            return source;
        }
        if (data != null) {
            source.put(JSON_TYPE, "base64");
            if (mediaType != null) {
                source.put("media_type", mediaType);
            }
            source.put("data", data);
            return source;
        }
        source.put(JSON_TYPE, "url");
        if (url != null) {
            source.put("url", url);
        }
        return source;
    }

    private static boolean isImage(String mediaType) {
        return mediaType != null && mediaType.regionMatches(true, 0, "image/", 0, "image/".length());
    }

    private JsonNode input(JsonNode arguments) {
        var parsed = AiProviderJsonSupport.parseArguments(mapper, arguments);
        return parsed != null && parsed.isObject() ? parsed : mapper.createObjectNode();
    }

    private void writeTools(ArrayNode toolsNode, List<AiTool> tools) {
        for (var tool : tools) {
            var toolNode = toolsNode.addObject();
            if (SERVER_TOOLS.contains(tool.name())) {
                toolNode.put(JSON_TYPE, tool.name());
                toolNode.put("name", tool.name());
                continue;
            }
            toolNode.put("name", tool.name());
            if (tool.description() != null) {
                toolNode.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                toolNode.put("input_schema", tool.parameters());
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
    }
}
