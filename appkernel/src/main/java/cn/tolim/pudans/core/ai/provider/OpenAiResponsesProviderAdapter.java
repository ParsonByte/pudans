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
public class OpenAiResponsesProviderAdapter extends AbstractAiProviderAdapter {

    static final String RESPONSES_PATH = "/v1/responses";

    private static final String PROTOCOL = "OpenAI Responses";

    private static final String ASSISTANT_ROLE = "assistant";

    private static final String TOOL_ROLE = "tool";

    private static final String USER_ROLE = "user";

    private final ObjectMapper mapper;

    public OpenAiResponsesProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, accountService);
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.OPENAI_RESPONSES;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            return post(provider, account, RESPONSES_PATH, serialize(request, model, false), false)
                    .map(this::parseChatResponse);
        });
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            return postStream(provider, account, RESPONSES_PATH, serialize(request, model, true))
                    .concatMap(data -> Mono.justOrEmpty(parseStreamEvent(data)));
        });
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

    String serialize(AiChatIr request, AiModel model, boolean stream) {
        var prepared = AiProviderHttpSupport.withDefaultMaxTokens(
                AiProviderHttpSupport.withModelAndStream(request, model, stream), maxOutputTokens(model));
        var params = prepared.requestParams();
        var root = mapper.createObjectNode();
        if (params.model() != null) {
            root.put("model", params.model());
        }
        var instructions = new StringBuilder();
        var input = root.putArray("input");
        for (var message : prepared.messages()) {
            if (AiProviderIrSupport.isSystem(message.role())) {
                AiProviderIrSupport.appendLine(instructions, AiProviderIrSupport.text(message.parts()));
            } else {
                writeMessage(input, message);
            }
        }
        if (!instructions.isEmpty()) {
            root.put("instructions", instructions.toString());
        }
        if (!prepared.tools().isEmpty()) {
            writeTools(root.putArray("tools"), prepared.tools());
        }
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
        root.put("stream", stream);
        return AiProviderJsonSupport.write(mapper, root, PROTOCOL);
    }

    AiChatResponse parseChatResponse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, PROTOCOL);
        failOnError(root);
        var output = AiProviderHttpSupport.value(root, "output");
        if (output == null || !output.isArray()) {
            throw new AiProtocolException("OpenAI Responses 响应缺少 output 数组");
        }
        var parts = new ArrayList<AiContentPart>();
        for (var item : output) {
            addOutputItem(parts, item);
        }
        return new AiChatResponse(
                AiProviderHttpSupport.text(root, "id"),
                AiProviderHttpSupport.text(root, "model"),
                new AiChatMessage(ASSISTANT_ROLE, parts),
                usage(AiProviderHttpSupport.value(root, "usage")),
                finishReason(root));
    }

    Optional<AiStreamEvent> parseStreamEvent(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        var root = AiProviderHttpSupport.readObject(mapper, data.strip(), PROTOCOL);
        var type = AiProviderHttpSupport.text(root, "type");
        if (type == null) {
            throw new AiProtocolException("OpenAI Responses 流事件缺少 type");
        }
        return switch (type) {
            case "response.output_text.delta" -> event(
                    AiProviderHttpSupport.text(root, "delta"), null, null, null, null);
            case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> event(
                    null, AiProviderHttpSupport.text(root, "delta"), null, null, null);
            case "response.function_call_arguments.delta" -> event(
                    null, null, argumentsDelta(root), null, null);
            case "response.output_item.added" -> outputItemAdded(root);
            case "response.completed", "response.incomplete" -> completed(root);
            case "response.failed" -> throw new AiProtocolException(
                    "OpenAI Responses 流式错误: " + errorMessage(root.get("response")));
            case "error" -> throw new AiProtocolException(
                    "OpenAI Responses 流式错误: " + errorMessage(root));
            default -> Optional.empty();
        };
    }

    private void writeMessage(ArrayNode input, AiChatMessage message) {
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.ToolResult result) {
                var item = input.addObject();
                item.put("type", "function_call_output");
                if (result.toolCallId() != null) {
                    item.put("call_id", result.toolCallId());
                }
                item.put("output", AiProviderJsonSupport.contentText(result.content()));
            }
        }
        var text = AiProviderIrSupport.text(message.parts());
        if (ASSISTANT_ROLE.equals(message.role())) {
            if (!text.isEmpty()) {
                input.addObject().put("role", ASSISTANT_ROLE).put("content", text);
            }
            for (var part : message.parts()) {
                if (part instanceof AiContentPart.ToolCall call) {
                    writeToolCall(input, call);
                }
            }
            return;
        }
        if (!text.isEmpty()) {
            var role = TOOL_ROLE.equals(message.role()) ? USER_ROLE : message.role();
            input.addObject().put("role", role).put("content", text);
        }
    }

    private void writeToolCall(ArrayNode input, AiContentPart.ToolCall call) {
        var item = input.addObject();
        item.put("type", "function_call");
        if (call.id() != null) {
            item.put("call_id", call.id());
        }
        if (call.name() != null) {
            item.put("name", call.name());
        }
        item.put("arguments", AiProviderJsonSupport.argumentsText(mapper, call.arguments(), PROTOCOL));
    }

    private void writeTools(ArrayNode toolsNode, List<AiTool> tools) {
        for (var tool : tools) {
            var toolNode = toolsNode.addObject();
            toolNode.put("type", "function");
            toolNode.put("name", tool.name());
            if (tool.description() != null) {
                toolNode.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                toolNode.set("parameters", tool.parameters());
            }
        }
    }

    private JsonNode toolChoice(JsonNode toolChoice) {
        var function = toolChoice.get("function");
        if (function != null && function.isObject() && function.hasNonNull("name")) {
            var converted = mapper.createObjectNode();
            converted.put("type", "function");
            converted.put("name", function.get("name").asText());
            return converted;
        }
        return toolChoice;
    }

    private void addOutputItem(List<AiContentPart> parts, JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new AiProtocolException("OpenAI Responses 响应 output 元素必须是 JSON 对象");
        }
        var type = AiProviderHttpSupport.text(item, "type");
        if (type == null) {
            throw new AiProtocolException("OpenAI Responses 响应 output 元素缺少 type");
        }
        switch (type) {
            case "message" -> addContent(parts, item.get("content"));
            case "function_call" -> parts.add(toolCallPart(item));
            case "reasoning" -> addReasoning(parts, item);
            case "output_text" -> parts.add(
                    new AiContentPart.Text(AiProviderHttpSupport.text(item, "text"), null));
            default -> parts.add(new AiContentPart.Custom(type, item));
        }
    }

    private void addContent(List<AiContentPart> parts, JsonNode content) {
        if (content == null || content.isNull()) {
            return;
        }
        if (content.isTextual()) {
            parts.add(new AiContentPart.Text(content.textValue(), null));
            return;
        }
        if (!content.isArray()) {
            throw new AiProtocolException("OpenAI Responses 响应 content 必须是字符串或数组");
        }
        for (var block : content) {
            if (block == null || !block.isObject()) {
                throw new AiProtocolException("OpenAI Responses 响应 content 元素必须是 JSON 对象");
            }
            var type = AiProviderHttpSupport.text(block, "type");
            if ("output_text".equals(type)) {
                parts.add(new AiContentPart.Text(AiProviderHttpSupport.text(block, "text"), null));
            } else {
                parts.add(new AiContentPart.Custom(type == null ? "content" : type, block));
            }
        }
    }

    private void addReasoning(List<AiContentPart> parts, JsonNode item) {
        var text = new StringBuilder();
        addReasoningTexts(text, item.get("summary"));
        addReasoningTexts(text, item.get("content"));
        if (!text.isEmpty()) {
            parts.add(new AiContentPart.Thinking(text.toString(), null, null));
        }
    }

    private static void addReasoningTexts(StringBuilder text, JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            return;
        }
        for (var block : blocks) {
            AiProviderIrSupport.appendLine(text, AiProviderHttpSupport.text(block, "text"));
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
                AiProviderJsonSupport.parseArguments(mapper, AiProviderHttpSupport.text(item, "arguments")),
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
                || !"function_call".equals(AiProviderHttpSupport.text(item, "type"))) {
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

    private Optional<AiStreamEvent> completed(JsonNode root) {
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

    private static AiTokenUsage usage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        var details = usageNode.get("input_tokens_details");
        var cached = details != null && details.isObject()
                ? AiProviderHttpSupport.integer(details, "cached_tokens")
                : null;
        var input = AiProviderHttpSupport.integer(usageNode, "input_tokens");
        var output = AiProviderHttpSupport.integer(usageNode, "output_tokens");
        if (input == null && output == null && cached == null) {
            return null;
        }
        return new AiTokenUsage(input, output, cached, null);
    }

    private static String finishReason(JsonNode root) {
        var incomplete = AiProviderHttpSupport.value(root, "incomplete_details");
        var reason = AiProviderHttpSupport.text(incomplete, "reason");
        return reason != null ? reason : AiProviderHttpSupport.text(root, "status");
    }

    private static void failOnError(JsonNode root) {
        if (AiProviderHttpSupport.value(root, "error") != null) {
            throw new AiProtocolException("OpenAI Responses 错误: " + errorMessage(root));
        }
    }

    private static String errorMessage(JsonNode node) {
        var error = AiProviderHttpSupport.value(node, "error");
        var message = AiProviderHttpSupport.text(error, "message");
        if (message == null) {
            message = AiProviderHttpSupport.text(node, "message");
        }
        return message == null ? "未知错误" : message;
    }

    private static Integer maxOutputTokens(AiModel model) {
        var spec = model == null ? null : model.getSpec();
        return spec == null ? null : spec.getMaxOutputTokens();
    }
}
