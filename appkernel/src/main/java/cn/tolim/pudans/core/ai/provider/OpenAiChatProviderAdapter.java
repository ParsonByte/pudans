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
import cn.tolim.pudans.core.ai.gateway.protocol.OpenAiChatCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
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
public class OpenAiChatProviderAdapter extends AbstractAiProviderAdapter {

    static final String CHAT_PATH = "/v1/chat/completions";

    static final String SSE_DONE = "[DONE]";

    private static final String PROTOCOL = "OpenAI Chat";

    private static final String DEFAULT_ROLE = "assistant";

    private final ObjectMapper mapper;

    private final OpenAiChatCodec codec;

    public OpenAiChatProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, accountService);
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.codec = new OpenAiChatCodec(this.mapper);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.OPENAI_CHAT;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> post(
                        provider, account, path(), serialize(request, model, false), false)
                .map(this::parseChatResponse));
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> postStream(provider, account, path(), serialize(request, model, true))
                .takeWhile(data -> !SSE_DONE.equals(data.strip()))
                .concatMap(data -> Mono.justOrEmpty(parseStreamEvent(data))));
    }

    protected String path() {
        return CHAT_PATH;
    }

    @Override
    protected HttpHeaders buildHeaders(AiProvider provider, String apiKey, boolean stream) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(stream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    protected AiChatResponse parseChatResponse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, PROTOCOL);
        validateResponse(root);
        var choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new AiProtocolException("OpenAI Chat 响应缺少 choices");
        }
        var choice = choices.get(0);
        var messageNode = choice.get("message");
        if (messageNode == null || !messageNode.isObject()) {
            throw new AiProtocolException("OpenAI Chat 响应缺少 message");
        }
        var parts = new ArrayList<AiContentPart>();
        var reasoning = AiProviderHttpSupport.text(messageNode, "reasoning_content");
        if (reasoning != null && !reasoning.isEmpty()) {
            parts.add(new AiContentPart.Thinking(reasoning, null, null));
        }
        addContent(parts, messageNode.get("content"));
        addToolCalls(parts, messageNode.get("tool_calls"));
        var role = AiProviderHttpSupport.text(messageNode, "role");
        return new AiChatResponse(
                AiProviderHttpSupport.text(root, "id"),
                AiProviderHttpSupport.text(root, "model"),
                new AiChatMessage(role == null ? DEFAULT_ROLE : role, parts),
                usage(root.get("usage")),
                AiProviderHttpSupport.text(choice, "finish_reason"));
    }

    protected Optional<AiStreamEvent> parseStreamEvent(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        var trimmed = data.strip();
        if (SSE_DONE.equals(trimmed)) {
            return Optional.empty();
        }
        var root = AiProviderHttpSupport.readObject(mapper, trimmed, PROTOCOL);
        validateStreamEvent(root);
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

    protected String serialize(AiChatIr request, AiModel model, boolean stream) {
        return codec.serialize(AiProviderHttpSupport.withModelAndStream(request, model, stream));
    }

    protected void validateResponse(JsonNode root) {}

    protected void validateStreamEvent(JsonNode root) {}

    private void addContent(List<AiContentPart> parts, JsonNode content) {
        if (content == null || content.isNull()) {
            return;
        }
        if (content.isTextual()) {
            parts.add(new AiContentPart.Text(content.textValue(), null));
            return;
        }
        if (!content.isArray()) {
            throw new AiProtocolException("OpenAI Chat 响应 content 必须是字符串或数组");
        }
        for (var item : content) {
            if (!item.isObject()) {
                throw new AiProtocolException("OpenAI Chat 响应 content 元素必须是 JSON 对象");
            }
            var type = AiProviderHttpSupport.text(item, "type");
            if ("text".equals(type)) {
                parts.add(new AiContentPart.Text(AiProviderHttpSupport.text(item, "text"), null));
            } else {
                parts.add(new AiContentPart.Custom(type, item));
            }
        }
    }

    private void addToolCalls(List<AiContentPart> parts, JsonNode toolCalls) {
        if (toolCalls == null || toolCalls.isNull()) {
            return;
        }
        if (!toolCalls.isArray()) {
            throw new AiProtocolException("OpenAI Chat 响应 tool_calls 必须是数组");
        }
        for (var toolCall : toolCalls) {
            if (!toolCall.isObject()) {
                throw new AiProtocolException("OpenAI Chat 响应 tool_calls 元素必须是 JSON 对象");
            }
            var function = toolCall.get("function");
            if (function == null || !function.isObject()) {
                parts.add(new AiContentPart.Custom("tool_call", toolCall));
                continue;
            }
            parts.add(new AiContentPart.ToolCall(
                    AiProviderHttpSupport.text(toolCall, "id"),
                    AiProviderHttpSupport.text(function, "name"),
                    arguments(AiProviderHttpSupport.text(function, "arguments")),
                    null));
        }
    }

    private AiStreamEvent.ToolCallDelta toolCall(JsonNode toolCalls) {
        if (toolCalls == null || !toolCalls.isArray() || toolCalls.isEmpty()) {
            return null;
        }
        var call = toolCalls.get(0);
        if (!call.isObject()) {
            throw new AiProtocolException("OpenAI Chat 流式响应 tool_calls 元素必须是 JSON 对象");
        }
        var function = call.get("function");
        var functionNode = function != null && function.isObject() ? function : null;
        return new AiStreamEvent.ToolCallDelta(
                AiProviderHttpSupport.integer(call, "index"),
                AiProviderHttpSupport.text(call, "id"),
                AiProviderHttpSupport.text(functionNode, "name"),
                AiProviderHttpSupport.text(functionNode, "arguments"));
    }

    private JsonNode arguments(String arguments) {
        if (arguments == null) {
            return null;
        }
        try {
            return mapper.readTree(arguments);
        } catch (JsonProcessingException e) {
            return mapper.getNodeFactory().textNode(arguments);
        }
    }

    private AiTokenUsage usage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        var details = usageNode.get("prompt_tokens_details");
        var cacheRead = details != null && details.isObject()
                ? AiProviderHttpSupport.integer(details, "cached_tokens")
                : null;
        var input = AiProviderHttpSupport.integer(usageNode, "prompt_tokens");
        var output = AiProviderHttpSupport.integer(usageNode, "completion_tokens");
        if (input == null && output == null && cacheRead == null) {
            return null;
        }
        return new AiTokenUsage(input, output, cacheRead, null);
    }
}
