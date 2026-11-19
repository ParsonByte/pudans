package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderIrSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

final class DeepSeekResponsesParser {

    private static final String ASSISTANT_ROLE = "assistant";

    private static final String JSON_TYPE = "type";

    private final ObjectMapper mapper;

    DeepSeekResponsesParser(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    AiChatResponse parse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        failOnError(root);
        var output = root.get("output");
        if (output == null || !output.isArray()) {
            throw new AiProtocolException("DeepSeek Responses 响应缺少 output 数组");
        }
        var parts = new ArrayList<AiContentPart>();
        for (var item : output) {
            addOutputItem(parts, item);
        }
        var title = AiProviderHttpSupport.text(root, "title");
        if (title != null) {
            parts.add(new AiContentPart.Custom("title", mapper.getNodeFactory().textNode(title)));
        }
        return new AiChatResponse(
                AiProviderHttpSupport.text(root, "id"),
                AiProviderHttpSupport.text(root, "model"),
                new AiChatMessage(ASSISTANT_ROLE, parts),
                usage(root.get("usage")),
                finishReason(root));
    }

    Optional<AiStreamEvent> parseStreamEvent(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        var root = AiProviderHttpSupport.readObject(mapper, data.strip(), DeepSeekProtocol.NAME);
        var type = AiProviderHttpSupport.text(root, JSON_TYPE);
        if (type == null) {
            throw new AiProtocolException("DeepSeek Responses 流事件缺少 type");
        }
        return switch (type) {
            case "response.output_text.delta" -> event(
                    AiProviderHttpSupport.text(root, "delta"), null, null, null, null);
            case "response.reasoning_text.delta" -> event(
                    null, AiProviderHttpSupport.text(root, "delta"), null, null, null);
            case "response.function_call_arguments.delta" -> event(
                    null, null, argumentsDelta(root), null, null);
            case "response.output_item.added" -> outputItemAdded(root);
            case "response.completed", "response.incomplete" -> completed(root);
            case "response.failed" -> throw new AiProtocolException(
                    "DeepSeek Responses 流式错误: " + errorMessage(root.get("response")));
            case "error" -> throw new AiProtocolException(
                    "DeepSeek Responses 流式错误: " + errorMessage(root));
            default -> Optional.empty();
        };
    }

    private void addOutputItem(ArrayList<AiContentPart> parts, JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new AiProtocolException("DeepSeek Responses 响应 output 元素必须是 JSON 对象");
        }
        var type = AiProviderHttpSupport.text(item, JSON_TYPE);
        if (type == null) {
            throw new AiProtocolException("DeepSeek Responses 响应 output 元素缺少 type");
        }
        switch (type) {
            case "message" -> addMessageContent(parts, item.get("content"));
            case "reasoning" -> addReasoning(parts, item);
            case "function_call" -> parts.add(toolCallPart(item));
            default -> parts.add(new AiContentPart.Custom(type, item));
        }
    }

    private void addMessageContent(ArrayList<AiContentPart> parts, JsonNode content) {
        if (content == null || content.isNull()) {
            return;
        }
        if (content.isTextual()) {
            parts.add(new AiContentPart.Text(content.textValue(), null));
            return;
        }
        if (!content.isArray()) {
            throw new AiProtocolException("DeepSeek Responses 响应 content 必须是字符串或数组");
        }
        for (var block : content) {
            if (block == null || !block.isObject()) {
                throw new AiProtocolException("DeepSeek Responses 响应 content 元素必须是 JSON 对象");
            }
            var type = AiProviderHttpSupport.text(block, JSON_TYPE);
            if ("output_text".equals(type)) {
                parts.add(new AiContentPart.Text(AiProviderHttpSupport.text(block, "text"), null));
                var annotations = AiProviderHttpSupport.value(block, "annotations");
                if (annotations != null) {
                    parts.add(new AiContentPart.Custom("annotations", annotations));
                }
            } else {
                parts.add(new AiContentPart.Custom(type == null ? "content" : type, block));
            }
        }
    }

    private static void addReasoning(ArrayList<AiContentPart> parts, JsonNode item) {
        var text = new StringBuilder();
        addReasoningTexts(text, item.get("content"));
        addReasoningTexts(text, item.get("summary"));
        if (!text.isEmpty()) {
            parts.add(new AiContentPart.Thinking(text.toString(), null, null));
        }
    }

    private static void addReasoningTexts(StringBuilder text, JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            return;
        }
        for (var block : blocks) {
            AiProviderIrSupport.appendLine(
                    text, AiProviderHttpSupport.text(block, "text"));
        }
    }

    private AiContentPart.ToolCall toolCallPart(JsonNode item) {
        var callId = AiProviderHttpSupport.text(item, "call_id");
        if (callId == null) {
            callId = AiProviderHttpSupport.text(item, "id");
        }
        return new AiContentPart.ToolCall(
                callId,
                AiProviderHttpSupport.text(item, "name"),
                AiProviderJsonSupport.parseArguments(
                        mapper, AiProviderHttpSupport.text(item, "arguments")),
                null);
    }

    private static AiStreamEvent.ToolCallDelta argumentsDelta(JsonNode root) {
        return new AiStreamEvent.ToolCallDelta(
                AiProviderHttpSupport.integer(root, "output_index"),
                AiProviderHttpSupport.text(root, "item_id"),
                null,
                AiProviderHttpSupport.text(root, "delta"));
    }

    private static Optional<AiStreamEvent> outputItemAdded(JsonNode root) {
        var item = root.get("item");
        if (item == null || !item.isObject()
                || !"function_call".equals(AiProviderHttpSupport.text(item, JSON_TYPE))) {
            return Optional.empty();
        }
        var callId = AiProviderHttpSupport.text(item, "call_id");
        if (callId == null) {
            callId = AiProviderHttpSupport.text(item, "id");
        }
        var delta = new AiStreamEvent.ToolCallDelta(
                AiProviderHttpSupport.integer(root, "output_index"),
                callId,
                AiProviderHttpSupport.text(item, "name"),
                null);
        return event(null, null, delta, null, null);
    }

    private static Optional<AiStreamEvent> completed(JsonNode root) {
        var response = root.get("response");
        return event(
                null,
                null,
                null,
                usage(AiProviderHttpSupport.value(response, "usage")),
                finishReason(response));
    }

    private static Optional<AiStreamEvent> event(
            String content,
            String reasoning,
            AiStreamEvent.ToolCallDelta toolCall,
            AiTokenUsage usage,
            String finishReason) {
        if (content == null && reasoning == null && toolCall == null && usage == null && finishReason == null) {
            return Optional.empty();
        }
        return Optional.of(new AiStreamEvent(content, reasoning, toolCall, usage, finishReason));
    }

    private static String finishReason(JsonNode response) {
        var status = AiProviderHttpSupport.text(response, "status");
        if ("incomplete".equals(status)) {
            var reason = AiProviderHttpSupport.text(response.get("incomplete_details"), "reason");
            return "max_output_tokens".equals(reason) ? "length" : "content_filter";
        }
        return "failed".equals(status) ? "failed" : "stop";
    }

    private static AiTokenUsage usage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        var input = AiProviderHttpSupport.integer(usageNode, "input_tokens");
        var output = AiProviderHttpSupport.integer(usageNode, "output_tokens");
        var details = usageNode.get("input_tokens_details");
        var cacheRead = details != null && details.isObject()
                ? AiProviderHttpSupport.integer(details, "cached_tokens")
                : null;
        if (input == null && output == null && cacheRead == null) {
            return null;
        }
        return new AiTokenUsage(input, output, cacheRead, null);
    }

    private static void failOnError(JsonNode root) {
        var error = root.get("error");
        if (error != null && error.isObject()) {
            throw new AiProtocolException("DeepSeek Responses 错误: " + errorMessage(root));
        }
    }

    private static String errorMessage(JsonNode node) {
        var error = node == null ? null : node.get("error");
        var message = error != null && error.isObject()
                ? AiProviderHttpSupport.text(error, "message")
                : AiProviderHttpSupport.text(node, "message");
        return message == null ? "未知错误" : message;
    }
}
