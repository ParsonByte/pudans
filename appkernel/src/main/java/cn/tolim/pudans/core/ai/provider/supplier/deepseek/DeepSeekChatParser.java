package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

final class DeepSeekChatParser {

    private static final String DONE = "[DONE]";

    private static final String JSON_TYPE = "type";

    private static final String DEFAULT_ROLE = "assistant";

    private final ObjectMapper mapper;

    DeepSeekChatParser(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    AiChatResponse parse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new AiProtocolException("DeepSeek Chat 响应缺少 choices");
        }
        var choice = choices.get(0);
        var message = choice.get("message");
        if (message == null || !message.isObject()) {
            throw new AiProtocolException("DeepSeek Chat 响应缺少 message");
        }
        var parts = new ArrayList<AiContentPart>();
        addReasoning(parts, message);
        addContent(parts, message.get("content"));
        addToolCalls(parts, message.get("tool_calls"));
        addMessageField(parts, message, "annotations");
        addMessageField(parts, message, "server_tool_calls");
        addMessageField(parts, message, "title");
        addMessageField(parts, message, "refusal");
        var role = AiProviderHttpSupport.text(message, "role");
        return new AiChatResponse(
                AiProviderHttpSupport.text(root, "id"),
                AiProviderHttpSupport.text(root, "model"),
                new AiChatMessage(role == null ? DEFAULT_ROLE : role, parts),
                usage(root.get("usage")),
                AiProviderHttpSupport.text(choice, "finish_reason"));
    }

    Optional<AiStreamEvent> parseStreamEvent(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        var trimmed = data.strip();
        if (DONE.equals(trimmed)) {
            return Optional.empty();
        }
        var root = AiProviderHttpSupport.readObject(mapper, trimmed, DeepSeekProtocol.NAME);
        rejectError(root);
        String content = null;
        String reasoning = null;
        String finishReason = null;
        AiStreamEvent.ToolCallDelta toolCall = null;
        var choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            var choice = choices.get(0);
            finishReason = AiProviderHttpSupport.text(choice, "finish_reason");
            var delta = choice.get("delta");
            if (delta != null && delta.isObject()) {
                content = AiProviderHttpSupport.text(delta, "content");
                reasoning = AiProviderHttpSupport.text(delta, "reasoning_content");
                toolCall = toolCall(delta.get("tool_calls"));
            }
        }
        var usage = usage(root.get("usage"));
        if (content == null && reasoning == null && toolCall == null && usage == null && finishReason == null) {
            return Optional.empty();
        }
        return Optional.of(new AiStreamEvent(content, reasoning, toolCall, usage, finishReason));
    }

    private void addReasoning(ArrayList<AiContentPart> parts, JsonNode message) {
        var reasoning = AiProviderHttpSupport.text(message, "reasoning_content");
        if (reasoning != null && !reasoning.isEmpty()) {
            parts.add(new AiContentPart.Thinking(reasoning, null, null));
        }
    }

    private void addContent(ArrayList<AiContentPart> parts, JsonNode content) {
        if (content == null || content.isNull()) {
            return;
        }
        if (content.isTextual()) {
            parts.add(new AiContentPart.Text(content.textValue(), null));
            return;
        }
        if (!content.isArray()) {
            throw new AiProtocolException("DeepSeek Chat 响应 content 必须是字符串或数组");
        }
        for (var block : content) {
            if (block == null || !block.isObject()) {
                throw new AiProtocolException("DeepSeek Chat 响应 content 元素必须是 JSON 对象");
            }
            var type = AiProviderHttpSupport.text(block, JSON_TYPE);
            if ("text".equals(type)) {
                parts.add(new AiContentPart.Text(AiProviderHttpSupport.text(block, "text"), null));
            } else {
                parts.add(new AiContentPart.Custom(type == null ? "content" : type, block));
            }
        }
    }

    private void addToolCalls(ArrayList<AiContentPart> parts, JsonNode toolCalls) {
        if (toolCalls == null || toolCalls.isNull()) {
            return;
        }
        if (!toolCalls.isArray()) {
            throw new AiProtocolException("DeepSeek Chat 响应 tool_calls 必须是数组");
        }
        for (var toolCall : toolCalls) {
            if (!toolCall.isObject()) {
                throw new AiProtocolException("DeepSeek Chat 响应 tool_calls 元素必须是 JSON 对象");
            }
            var function = toolCall.get("function");
            parts.add(new AiContentPart.ToolCall(
                    AiProviderHttpSupport.text(toolCall, "id"),
                    AiProviderHttpSupport.text(function, "name"),
                    AiProviderJsonSupport.parseArguments(
                            mapper, AiProviderHttpSupport.text(function, "arguments")),
                    null));
        }
    }

    private static void addMessageField(ArrayList<AiContentPart> parts, JsonNode message, String field) {
        var value = AiProviderHttpSupport.value(message, field);
        if (value != null) {
            parts.add(new AiContentPart.Custom(field, value));
        }
    }

    private AiStreamEvent.ToolCallDelta toolCall(JsonNode toolCalls) {
        if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
            return null;
        }
        var call = toolCalls.get(0);
        if (!call.isObject()) {
            throw new AiProtocolException("DeepSeek Chat 流式响应 tool_calls 元素必须是 JSON 对象");
        }
        var function = call.get("function");
        return new AiStreamEvent.ToolCallDelta(
                AiProviderHttpSupport.integer(call, "index"),
                AiProviderHttpSupport.text(call, "id"),
                AiProviderHttpSupport.text(function, "name"),
                AiProviderHttpSupport.text(function, "arguments"));
    }

    private static void rejectError(JsonNode root) {
        var error = root.get("error");
        if (error == null || !error.isObject()) {
            return;
        }
        throw new AiProtocolException("DeepSeek Chat 流式错误: " + AiProviderHttpSupport.text(error, "message"));
    }

    private static AiTokenUsage usage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        var input = AiProviderHttpSupport.integer(usageNode, "prompt_tokens");
        var output = AiProviderHttpSupport.integer(usageNode, "completion_tokens");
        var details = usageNode.get("prompt_tokens_details");
        var cacheRead = details != null && details.isObject()
                ? AiProviderHttpSupport.integer(details, "cached_tokens")
                : null;
        if (cacheRead == null) {
            cacheRead = AiProviderHttpSupport.integer(usageNode, "prompt_cache_hit_tokens");
        }
        if (input == null && output == null && cacheRead == null) {
            return null;
        }
        return new AiTokenUsage(input, output, cacheRead, null);
    }
}
