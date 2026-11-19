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
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class OllamaProviderAdapter extends AbstractAiProviderAdapter {

    static final String CHAT_PATH = "/api/chat";

    private static final String PROTOCOL = "Ollama";

    private static final String ASSISTANT_ROLE = "assistant";

    private static final String TOOL_ROLE = "tool";

    private static final String SYSTEM_ROLE = "system";

    private static final String TOOL_NAME_FALLBACK = "tool";

    private final ObjectMapper mapper;

    public OllamaProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, accountService);
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.OLLAMA;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            return post(provider, account, CHAT_PATH, serialize(prepare(request, model, false)), false)
                    .map(this::parseChatResponse);
        });
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            return postStream(provider, account, CHAT_PATH, serialize(prepare(request, model, true)))
                    .flatMapIterable(line -> line.lines().toList())
                    .concatMap(line -> Flux.fromIterable(parseStreamEvents(line)));
        });
    }

    @Override
    protected HttpHeaders buildHeaders(AiProvider provider, String apiKey, boolean stream) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(stream ? MediaType.APPLICATION_NDJSON : MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    static AiChatIr prepare(AiChatIr request, AiModel model, boolean stream) {
        return AiProviderHttpSupport.withDefaultMaxTokens(
                AiProviderHttpSupport.withModelAndStream(request, model, stream), maxOutputTokens(model));
    }

    String serialize(AiChatIr prepared) {
        var params = prepared.requestParams();
        var root = mapper.createObjectNode();
        if (params.model() != null) {
            root.put("model", params.model());
        }
        var messages = root.putArray("messages");
        var toolNames = AiProviderIrSupport.toolNames(prepared.messages());
        for (var message : prepared.messages()) {
            writeMessage(messages, message, toolNames);
        }
        if (!prepared.tools().isEmpty()) {
            writeTools(root.putArray("tools"), prepared.tools());
        }
        if (params.temperature() != null || params.maxTokens() != null) {
            var options = root.putObject("options");
            if (params.temperature() != null) {
                options.put("temperature", params.temperature());
            }
            if (params.maxTokens() != null) {
                options.put("num_predict", params.maxTokens());
            }
        }
        root.put("stream", prepared.stream());
        return AiProviderJsonSupport.write(mapper, root, PROTOCOL);
    }

    AiChatResponse parseChatResponse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, PROTOCOL);
        failOnError(root);
        var messageNode = root.get("message");
        if (messageNode == null || !messageNode.isObject()) {
            throw new AiProtocolException("Ollama 响应缺少 message");
        }
        return new AiChatResponse(
                null,
                AiProviderHttpSupport.text(root, "model"),
                new AiChatMessage(ASSISTANT_ROLE, parts(messageNode)),
                usage(root),
                AiProviderHttpSupport.text(root, "done_reason"));
    }

    List<AiStreamEvent> parseStreamEvents(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        var root = AiProviderHttpSupport.readObject(mapper, line.strip(), PROTOCOL);
        failOnError(root);
        var events = new ArrayList<AiStreamEvent>();
        String content = null;
        String reasoning = null;
        var messageNode = root.get("message");
        if (messageNode != null && messageNode.isObject()) {
            content = emptyToNull(AiProviderHttpSupport.text(messageNode, "content"));
            reasoning = emptyToNull(AiProviderHttpSupport.text(messageNode, "thinking"));
        }
        var usage = usage(root);
        var finishReason = AiProviderHttpSupport.text(root, "done_reason");
        if (content != null || reasoning != null || usage != null || finishReason != null) {
            events.add(new AiStreamEvent(content, reasoning, null, usage, finishReason));
        }
        if (messageNode != null && messageNode.isObject()) {
            addStreamToolCalls(events, messageNode.get("tool_calls"));
        }
        return events;
    }

    private void writeMessage(ArrayNode messages, AiChatMessage message, Map<String, String> toolNames) {
        var content = new StringBuilder();
        var thinking = new StringBuilder();
        var images = new ArrayList<String>();
        var toolCalls = new ArrayList<AiContentPart.ToolCall>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text text) {
                content.append(text.text());
            } else if (part instanceof AiContentPart.Thinking value) {
                thinking.append(value.text());
            } else if (part instanceof AiContentPart.Image image && image.data() != null) {
                images.add(image.data());
            } else if (part instanceof AiContentPart.ToolCall call) {
                toolCalls.add(call);
            } else if (part instanceof AiContentPart.ToolResult result) {
                writeToolResult(messages, result, toolNames);
            }
        }
        if (content.isEmpty() && thinking.isEmpty() && images.isEmpty() && toolCalls.isEmpty()) {
            return;
        }
        var messageNode = messages.addObject();
        messageNode.put("role", AiProviderIrSupport.isSystem(message.role()) ? SYSTEM_ROLE : message.role());
        messageNode.put("content", content.toString());
        if (!thinking.isEmpty()) {
            messageNode.put("thinking", thinking.toString());
        }
        if (!images.isEmpty()) {
            var imageArray = messageNode.putArray("images");
            images.forEach(imageArray::add);
        }
        if (!toolCalls.isEmpty()) {
            var calls = messageNode.putArray("tool_calls");
            for (var call : toolCalls) {
                var function = calls.addObject().putObject("function");
                if (call.name() != null) {
                    function.put("name", call.name());
                }
                function.set("arguments", AiProviderJsonSupport.objectArguments(mapper, call.arguments()));
            }
        }
    }

    private void writeToolResult(
            ArrayNode messages, AiContentPart.ToolResult result, Map<String, String> toolNames) {
        var messageNode = messages.addObject();
        messageNode.put("role", TOOL_ROLE);
        messageNode.put("content", AiProviderJsonSupport.contentText(result.content()));
        messageNode.put("tool_name", toolName(result.toolCallId(), toolNames));
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
        }
    }

    private List<AiContentPart> parts(JsonNode messageNode) {
        var parts = new ArrayList<AiContentPart>();
        var thinking = AiProviderHttpSupport.text(messageNode, "thinking");
        if (thinking != null && !thinking.isEmpty()) {
            parts.add(new AiContentPart.Thinking(thinking, null, null));
        }
        var content = AiProviderHttpSupport.text(messageNode, "content");
        if (content != null && !content.isEmpty()) {
            parts.add(new AiContentPart.Text(content, null));
        }
        var toolCalls = messageNode.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (var call : toolCalls) {
                if (call == null || !call.isObject()) {
                    throw new AiProtocolException("Ollama 响应 tool_calls 元素必须是 JSON 对象");
                }
                var function = call.get("function");
                parts.add(new AiContentPart.ToolCall(
                        null,
                        AiProviderHttpSupport.text(function, "name"),
                        AiProviderJsonSupport.parseArguments(
                                mapper, AiProviderHttpSupport.value(function, "arguments")),
                        null));
            }
        }
        return parts;
    }

    private void addStreamToolCalls(List<AiStreamEvent> events, JsonNode toolCalls) {
        if (toolCalls == null || toolCalls.isNull()) {
            return;
        }
        if (!toolCalls.isArray()) {
            throw new AiProtocolException("Ollama 流式响应 tool_calls 必须是数组");
        }
        for (var position = 0; position < toolCalls.size(); position++) {
            var call = toolCalls.get(position);
            if (call == null || !call.isObject()) {
                throw new AiProtocolException("Ollama 流式响应 tool_calls 元素必须是 JSON 对象");
            }
            var function = call.get("function");
            events.add(new AiStreamEvent(
                    null,
                    null,
                    new AiStreamEvent.ToolCallDelta(
                            position,
                            null,
                            AiProviderHttpSupport.text(function, "name"),
                            AiProviderJsonSupport.argumentsText(
                                    mapper, AiProviderHttpSupport.value(function, "arguments"), PROTOCOL)),
                    null,
                    null));
        }
    }

    private static AiTokenUsage usage(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var input = AiProviderHttpSupport.integer(node, "prompt_eval_count");
        var output = AiProviderHttpSupport.integer(node, "eval_count");
        if (input == null && output == null) {
            return null;
        }
        return new AiTokenUsage(input, output, null, null);
    }

    private static void failOnError(JsonNode root) {
        var error = AiProviderHttpSupport.value(root, "error");
        if (error != null) {
            var message = error.isTextual() ? error.textValue() : AiProviderHttpSupport.text(error, "message");
            throw new AiProtocolException("Ollama 错误: " + (message == null ? "未知错误" : message));
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String toolName(String toolCallId, Map<String, String> toolNames) {
        if (toolCallId == null) {
            return TOOL_NAME_FALLBACK;
        }
        return toolNames.getOrDefault(toolCallId, TOOL_NAME_FALLBACK);
    }

    private static Integer maxOutputTokens(AiModel model) {
        var spec = model == null ? null : model.getSpec();
        return spec == null ? null : spec.getMaxOutputTokens();
    }
}
