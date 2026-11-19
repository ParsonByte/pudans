package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderIrSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class DeepSeekResponsesCodec {

    private static final String JSON_TYPE = "type";

    private static final String ASSISTANT_ROLE = "assistant";

    private static final Set<String> SERVER_TOOLS =
            Set.of("web_search", "deepseek_web_search", "deepseek_read_url");

    private static final Set<String> INPUT_ITEMS =
            Set.of("function_call", "function_call_output", "custom_tool_call",
                    "custom_tool_call_output", "reasoning", "web_search_call", "tool_search_call");

    private static final Set<String> RESPONSE_ONLY =
            Set.of("annotations", "title", "refusal", "server_tool_calls");

    private final ObjectMapper mapper;

    DeepSeekResponsesCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    String serialize(AiChatIr request) {
        var params = request.requestParams();
        var root = mapper.createObjectNode();
        params.extra().forEach(root::set);
        if (params.model() != null) {
            root.put("model", params.model());
        }
        var instructions = new StringBuilder();
        var input = root.putArray("input");
        var hasSystem = false;
        for (var message : request.messages()) {
            if (AiProviderIrSupport.isSystem(message.role())) {
                var text = AiProviderIrSupport.text(message.parts());
                if (!hasSystem) {
                    AiProviderIrSupport.appendLine(instructions, text);
                    hasSystem = true;
                } else if (!text.isEmpty()) {
                    addMessageItem(input, "system", List.of(new AiContentPart.Text(text, null)));
                }
            } else {
                writeMessage(input, message);
            }
        }
        if (!instructions.isEmpty()) {
            root.put("instructions", instructions.toString());
        }
        if (!request.tools().isEmpty()) {
            writeTools(root.putArray("tools"), request.tools());
        }
        writeParams(root, params);
        root.put("stream", request.stream());
        return AiProviderJsonSupport.write(mapper, root, DeepSeekProtocol.NAME);
    }

    private void writeMessage(ArrayNode input, AiChatMessage message) {
        if ("latest_reminder".equals(message.role())) {
            addMessageItem(input, "system", message.parts());
            return;
        }
        var content = new ArrayList<AiContentPart>();
        var reasoning = new ArrayList<AiContentPart.Thinking>();
        var toolCalls = new ArrayList<AiContentPart.ToolCall>();
        var toolResults = new ArrayList<AiContentPart.ToolResult>();
        var items = new ArrayList<JsonNode>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.ToolResult result) {
                toolResults.add(result);
            } else if (part instanceof AiContentPart.ToolCall call) {
                toolCalls.add(call);
            } else if (part instanceof AiContentPart.Thinking thinking) {
                reasoning.add(thinking);
            } else if (part instanceof AiContentPart.Custom custom && RESPONSE_ONLY.contains(custom.type())) {
                continue;
            } else if (part instanceof AiContentPart.Custom custom && isInputItem(custom.raw())) {
                items.add(custom.raw());
            } else {
                content.add(part);
            }
        }
        if (ASSISTANT_ROLE.equals(message.role())) {
            for (var thinking : reasoning) {
                addReasoning(input, thinking);
            }
            if (!content.isEmpty()) {
                addMessageItem(input, responsesRole(message.role()), content);
            }
            for (var call : toolCalls) {
                addFunctionCall(input, call);
            }
        } else {
            if (!content.isEmpty()) {
                addMessageItem(input, responsesRole(message.role()), content);
            }
            for (var result : toolResults) {
                addFunctionCallOutput(input, result);
            }
        }
        items.forEach(input::add);
    }

    private void addMessageItem(ArrayNode input, String role, List<AiContentPart> parts) {
        var item = input.addObject();
        item.put(JSON_TYPE, "message");
        item.put("role", role);
        if (parts.size() == 1 && parts.get(0) instanceof AiContentPart.Text text) {
            item.put("content", text.text());
            return;
        }
        var content = item.putArray("content");
        for (var part : parts) {
            writeContentBlock(content, role, part);
        }
    }

    private void writeContentBlock(ArrayNode content, String role, AiContentPart part) {
        if (part instanceof AiContentPart.Text text) {
            var block = content.addObject();
            block.put(JSON_TYPE, "assistant".equals(role) ? "output_text" : "input_text");
            block.put("text", text.text());
        } else if (part instanceof AiContentPart.Image image) {
            var block = content.addObject();
            block.put(JSON_TYPE, "input_image");
            if (image.url() != null) {
                block.put("image_url", image.url());
            } else if (image.data() != null) {
                var mediaType = image.mediaType() == null ? "application/octet-stream" : image.mediaType();
                block.put("image_url", "data:" + mediaType + ";base64," + image.data());
            }
            if (image.detail() != null) {
                block.put("detail", image.detail());
            }
        } else if (part instanceof AiContentPart.File file) {
            if (file.fileId() == null) {
                throw new AiProtocolException("DeepSeek Responses 文件块只支持 file_id 来源");
            }
            var block = content.addObject();
            block.put(JSON_TYPE, "input_file");
            block.put("file_id", file.fileId());
        } else if (part instanceof AiContentPart.Custom custom) {
            content.add(custom.raw());
        }
    }

    private void addReasoning(ArrayNode input, AiContentPart.Thinking thinking) {
        var item = input.addObject();
        item.put(JSON_TYPE, "reasoning");
        var content = item.putArray("content");
        var block = content.addObject();
        block.put(JSON_TYPE, "reasoning_text");
        block.put("text", thinking.text());
    }

    private void addFunctionCall(ArrayNode input, AiContentPart.ToolCall call) {
        var item = input.addObject();
        item.put(JSON_TYPE, "function_call");
        if (call.id() != null) {
            item.put("call_id", call.id());
        }
        if (call.name() != null) {
            item.put("name", call.name());
        }
        item.put("arguments",
                AiProviderJsonSupport.argumentsText(mapper, call.arguments(), DeepSeekProtocol.NAME));
    }

    private void addFunctionCallOutput(ArrayNode input, AiContentPart.ToolResult result) {
        var item = input.addObject();
        item.put(JSON_TYPE, "function_call_output");
        if (result.toolCallId() != null) {
            item.put("call_id", result.toolCallId());
        }
        if (result.content() == null || result.content().isNull()) {
            item.put("output", "");
        } else if (result.content().isTextual()) {
            item.put("output", result.content().textValue());
        } else if (result.content().isArray()) {
            item.set("output", result.content());
        } else {
            item.put("output", result.content().toString());
        }
    }

    private static boolean isInputItem(JsonNode raw) {
        return raw != null && raw.isObject()
                && INPUT_ITEMS.contains(AiProviderHttpSupport.text(raw, JSON_TYPE));
    }

    private void writeTools(ArrayNode toolsNode, List<AiTool> tools) {
        for (var tool : tools) {
            var toolNode = toolsNode.addObject();
            if (SERVER_TOOLS.contains(tool.name())) {
                toolNode.put(JSON_TYPE, tool.name());
                continue;
            }
            if ("apply_patch".equals(tool.name())) {
                toolNode.put(JSON_TYPE, "custom");
                toolNode.put("name", tool.name());
                continue;
            }
            toolNode.put(JSON_TYPE, "function");
            toolNode.put("name", tool.name());
            if (tool.description() != null) {
                toolNode.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                toolNode.set("parameters", tool.parameters());
            }
        }
    }

    private void writeParams(ObjectNode root, AiRequestParams params) {
        if (params.maxTokens() != null) {
            root.put("max_output_tokens", params.maxTokens());
        }
        if (params.temperature() != null) {
            root.put("temperature", params.temperature());
        }
        if (params.topP() != null) {
            root.put("top_p", params.topP());
        }
        if (params.toolChoice() != null) {
            root.set("tool_choice", toolChoice(params.toolChoice()));
        }
    }

    private JsonNode toolChoice(JsonNode toolChoice) {
        var function = toolChoice.get("function");
        if (function != null && function.isObject() && function.hasNonNull("name")) {
            var converted = mapper.createObjectNode();
            converted.put(JSON_TYPE, "function");
            converted.put("name", function.get("name").asText());
            return converted;
        }
        return toolChoice;
    }

    private static String responsesRole(String role) {
        if (AiProviderIrSupport.isSystem(role)) {
            return "system";
        }
        return "tool".equals(role) ? "user" : role;
    }
}
