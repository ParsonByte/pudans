package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

final class DeepSeekMessagesParser {

    private static final String DEFAULT_ROLE = "assistant";

    private static final String JSON_TYPE = "type";

    private final ObjectMapper mapper;

    DeepSeekMessagesParser(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    AiChatResponse parse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var content = root.get("content");
        if (content == null || !content.isArray()) {
            throw new AiProtocolException("DeepSeek 响应缺少 content 数组");
        }
        var parts = new ArrayList<AiContentPart>();
        for (var block : content) {
            parts.add(part(block));
        }
        var role = AiProviderHttpSupport.text(root, "role");
        return new AiChatResponse(
                AiProviderHttpSupport.text(root, "id"),
                AiProviderHttpSupport.text(root, "model"),
                new AiChatMessage(role == null ? DEFAULT_ROLE : role, parts),
                usage(root.get("usage")),
                AiProviderHttpSupport.text(root, "stop_reason"));
    }

    Optional<AiStreamEvent> parseStreamEvent(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        var root = AiProviderHttpSupport.readObject(mapper, data.strip(), DeepSeekProtocol.NAME);
        rejectError(root);
        var type = AiProviderHttpSupport.text(root, JSON_TYPE);
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case "message_start" -> messageStart(root);
            case "content_block_start" -> contentBlockStart(root);
            case "content_block_delta" -> contentBlockDelta(root);
            case "message_delta" -> messageDelta(root);
            default -> Optional.empty();
        };
    }

    private AiContentPart part(JsonNode block) {
        if (block == null || !block.isObject()) {
            throw new AiProtocolException("DeepSeek 响应 content 元素必须是 JSON 对象");
        }
        var type = AiProviderHttpSupport.text(block, JSON_TYPE);
        if (type == null) {
            throw new AiProtocolException("DeepSeek 响应 content 元素缺少 type");
        }
        return switch (type) {
            case "text" -> new AiContentPart.Text(AiProviderHttpSupport.text(block, "text"), null);
            case "thinking" -> new AiContentPart.Thinking(
                    AiProviderHttpSupport.text(block, "thinking"),
                    AiProviderHttpSupport.text(block, "signature"),
                    null);
            case "tool_use" -> new AiContentPart.ToolCall(
                    AiProviderHttpSupport.text(block, "id"),
                    AiProviderHttpSupport.text(block, "name"),
                    AiProviderHttpSupport.value(block, "input"),
                    null);
            default -> new AiContentPart.Custom(type, block);
        };
    }

    private void rejectError(JsonNode root) {
        var error = root.get("error");
        if (error == null || !error.isObject()) {
            return;
        }
        var message = AiProviderHttpSupport.text(error, "message");
        throw new AiProtocolException("DeepSeek 流式错误: " + (message == null ? "未知错误" : message));
    }

    private Optional<AiStreamEvent> messageStart(JsonNode root) {
        var message = root.get("message");
        if (message == null || !message.isObject()) {
            throw new AiProtocolException("DeepSeek message_start 事件缺少 message");
        }
        var usage = usage(message.get("usage"));
        if (usage == null) {
            return Optional.empty();
        }
        return Optional.of(new AiStreamEvent(null, null, null, usage, null));
    }

    private Optional<AiStreamEvent> contentBlockStart(JsonNode root) {
        var block = root.get("content_block");
        if (block == null || !block.isObject()
                || !"tool_use".equals(AiProviderHttpSupport.text(block, JSON_TYPE))) {
            return Optional.empty();
        }
        var delta = new AiStreamEvent.ToolCallDelta(
                AiProviderHttpSupport.integer(root, "index"),
                AiProviderHttpSupport.text(block, "id"),
                AiProviderHttpSupport.text(block, "name"),
                null);
        return Optional.of(new AiStreamEvent(null, null, delta, null, null));
    }

    private Optional<AiStreamEvent> contentBlockDelta(JsonNode root) {
        var delta = root.get("delta");
        if (delta == null || !delta.isObject()) {
            return Optional.empty();
        }
        var type = AiProviderHttpSupport.text(delta, JSON_TYPE);
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case "text_delta" -> Optional.of(new AiStreamEvent(
                    AiProviderHttpSupport.text(delta, "text"), null, null, null, null));
            case "thinking_delta" -> Optional.of(new AiStreamEvent(
                    null, AiProviderHttpSupport.text(delta, "thinking"), null, null, null));
            case "input_json_delta" -> Optional.of(new AiStreamEvent(
                    null,
                    null,
                    new AiStreamEvent.ToolCallDelta(
                            AiProviderHttpSupport.integer(root, "index"),
                            null,
                            null,
                            AiProviderHttpSupport.text(delta, "partial_json")),
                    null,
                    null));
            // 思考签名与网页引用只对 DeepSeek 自身续写有意义，网关事件不承载。
            default -> Optional.empty();
        };
    }

    private Optional<AiStreamEvent> messageDelta(JsonNode root) {
        var delta = root.get("delta");
        var finishReason = delta != null && delta.isObject()
                ? AiProviderHttpSupport.text(delta, "stop_reason")
                : null;
        var usage = usage(root.get("usage"));
        if (finishReason == null && usage == null) {
            return Optional.empty();
        }
        return Optional.of(new AiStreamEvent(null, null, null, usage, finishReason));
    }

    private AiTokenUsage usage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        var input = AiProviderHttpSupport.integer(usageNode, "input_tokens");
        var output = AiProviderHttpSupport.integer(usageNode, "output_tokens");
        var cacheRead = AiProviderHttpSupport.integer(usageNode, "cache_read_input_tokens");
        var cacheWrite = AiProviderHttpSupport.integer(usageNode, "cache_creation_input_tokens");
        if (input == null && output == null && cacheRead == null && cacheWrite == null) {
            return null;
        }
        return new AiTokenUsage(input, output, cacheRead, cacheWrite);
    }
}
