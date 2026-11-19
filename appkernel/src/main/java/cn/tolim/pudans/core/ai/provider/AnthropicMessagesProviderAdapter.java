package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.gateway.protocol.AnthropicMessagesCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AnthropicMessagesProviderAdapter extends AbstractAiProviderAdapter {

    static final String MESSAGES_PATH = "/v1/messages";

    static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String PROTOCOL = "Anthropic";

    private static final String DEFAULT_ROLE = "assistant";

    private final ObjectMapper mapper;

    private final AnthropicMessagesCodec codec;

    public AnthropicMessagesProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, accountService);
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.codec = new AnthropicMessagesCodec(this.mapper);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.ANTHROPIC;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> post(
                        provider, account, MESSAGES_PATH, serialize(request, model, false), false)
                .map(this::parseChatResponse));
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> postStream(provider, account, MESSAGES_PATH, serialize(request, model, true))
                .concatMap(data -> Mono.justOrEmpty(parseStreamEvent(data))));
    }

    @Override
    protected HttpHeaders buildHeaders(AiProvider provider, String apiKey, boolean stream) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(stream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.set("x-api-key", apiKey);
        }
        headers.set("anthropic-version", ANTHROPIC_VERSION);
        return headers;
    }

    AiChatResponse parseChatResponse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, PROTOCOL);
        var content = root.get("content");
        if (content == null || !content.isArray()) {
            throw new AiProtocolException("Anthropic 响应缺少 content 数组");
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
        var root = AiProviderHttpSupport.readObject(mapper, data.strip(), PROTOCOL);
        var type = AiProviderHttpSupport.text(root, "type");
        if (type == null) {
            throw new AiProtocolException("Anthropic 流事件缺少 type");
        }
        return switch (type) {
            case "message_start" -> messageStart(root);
            case "content_block_start" -> contentBlockStart(root);
            case "content_block_delta" -> contentBlockDelta(root);
            case "message_delta" -> messageDelta(root);
            case "error" -> throw new AiProtocolException("Anthropic 流式错误: " + errorMessage(root));
            default -> Optional.empty();
        };
    }

    private String serialize(AiChatIr request, AiModel model, boolean stream) {
        var prepared = AiProviderHttpSupport.withModelAndStream(request, model, stream);
        return codec.serialize(AiProviderHttpSupport.withDefaultMaxTokens(prepared, maxOutputTokens(model)));
    }

    private AiContentPart part(JsonNode block) {
        if (block == null || !block.isObject()) {
            throw new AiProtocolException("Anthropic 响应 content 元素必须是 JSON 对象");
        }
        var type = AiProviderHttpSupport.text(block, "type");
        if (type == null) {
            throw new AiProtocolException("Anthropic 响应 content 元素缺少 type");
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

    private Optional<AiStreamEvent> messageStart(JsonNode root) {
        var message = root.get("message");
        if (message == null || !message.isObject()) {
            throw new AiProtocolException("Anthropic message_start 事件缺少 message");
        }
        var usage = usage(message.get("usage"));
        if (usage == null) {
            return Optional.empty();
        }
        return Optional.of(new AiStreamEvent(
                null, null, null, usage, AiProviderHttpSupport.text(message, "stop_reason")));
    }

    private Optional<AiStreamEvent> contentBlockStart(JsonNode root) {
        var block = root.get("content_block");
        if (block == null || !block.isObject()
                || !"tool_use".equals(AiProviderHttpSupport.text(block, "type"))) {
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
        var type = AiProviderHttpSupport.text(delta, "type");
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

    private static Integer maxOutputTokens(AiModel model) {
        var spec = model == null ? null : model.getSpec();
        return spec == null ? null : spec.getMaxOutputTokens();
    }

    private static String errorMessage(JsonNode root) {
        var error = root.get("error");
        var message = error != null && error.isObject()
                ? AiProviderHttpSupport.text(error, "message")
                : null;
        return message == null ? "未知错误" : message;
    }
}
