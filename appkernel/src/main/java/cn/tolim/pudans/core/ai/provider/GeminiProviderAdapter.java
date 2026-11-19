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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class GeminiProviderAdapter extends AbstractAiProviderAdapter {

    static final String GENERATE_PREFIX = "/v1beta/models/";

    static final String GENERATE_SUFFIX = ":generateContent";

    static final String STREAM_SUFFIX = ":streamGenerateContent?alt=sse";

    private static final String PROTOCOL = "Gemini";

    private static final String ASSISTANT_ROLE = "assistant";

    private static final String MODEL_ROLE = "model";

    private static final String USER_ROLE = "user";

    private static final String TOOL_NAME_FALLBACK = "tool";

    private final ObjectMapper mapper;

    public GeminiProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, accountService);
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.GEMINI;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            var prepared = prepare(request, model, false);
            return post(provider, account, generatePath(prepared), serialize(prepared), false)
                    .map(this::parseChatResponse);
        });
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            var prepared = prepare(request, model, true);
            return postStream(provider, account, generatePath(prepared), serialize(prepared))
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
            headers.set("x-goog-api-key", apiKey);
        }
        return headers;
    }

    static AiChatIr prepare(AiChatIr request, AiModel model, boolean stream) {
        return AiProviderHttpSupport.withDefaultMaxTokens(
                AiProviderHttpSupport.withModelAndStream(request, model, stream), maxOutputTokens(model));
    }

    static String generatePath(AiChatIr prepared) {
        var modelCode = prepared.requestParams().model();
        if (!StringUtils.hasText(modelCode)) {
            throw new AiProtocolException("Gemini 请求缺少模型编码");
        }
        var suffix = prepared.stream() ? STREAM_SUFFIX : GENERATE_SUFFIX;
        return GENERATE_PREFIX + modelCode.strip() + suffix;
    }

    String serialize(AiChatIr prepared) {
        var params = prepared.requestParams();
        var root = mapper.createObjectNode();
        var system = new StringBuilder();
        var contents = root.putArray("contents");
        var toolNames = AiProviderIrSupport.toolNames(prepared.messages());
        for (var message : prepared.messages()) {
            if (AiProviderIrSupport.isSystem(message.role())) {
                AiProviderIrSupport.appendLine(system, AiProviderIrSupport.text(message.parts()));
            } else {
                writeMessage(contents, message, toolNames);
            }
        }
        if (!system.isEmpty()) {
            root.putObject("systemInstruction").putArray("parts").addObject().put("text", system.toString());
        }
        if (!prepared.tools().isEmpty()) {
            writeTools(root.putArray("tools"), prepared.tools());
        }
        if (params.maxTokens() != null || params.temperature() != null || params.topP() != null) {
            var generation = root.putObject("generationConfig");
            if (params.maxTokens() != null) {
                generation.put("maxOutputTokens", params.maxTokens());
            }
            if (params.temperature() != null) {
                generation.put("temperature", params.temperature());
            }
            if (params.topP() != null) {
                generation.put("topP", params.topP());
            }
        }
        return AiProviderJsonSupport.write(mapper, root, PROTOCOL);
    }

    AiChatResponse parseChatResponse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, PROTOCOL);
        failOnError(root);
        var candidate = firstCandidate(root);
        var content = AiProviderHttpSupport.value(candidate, "content");
        var parts = new ArrayList<AiContentPart>();
        addParts(parts, AiProviderHttpSupport.value(content, "parts"));
        return new AiChatResponse(
                AiProviderHttpSupport.text(root, "responseId"),
                AiProviderHttpSupport.text(root, "modelVersion"),
                new AiChatMessage(ASSISTANT_ROLE, parts),
                usage(AiProviderHttpSupport.value(root, "usageMetadata")),
                AiProviderHttpSupport.text(candidate, "finishReason"));
    }

    Optional<AiStreamEvent> parseStreamEvent(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        var root = AiProviderHttpSupport.readObject(mapper, data.strip(), PROTOCOL);
        failOnError(root);
        String content = null;
        String reasoning = null;
        AiStreamEvent.ToolCallDelta toolCall = null;
        String finishReason = null;
        var candidates = root.get("candidates");
        if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
            var candidate = candidates.get(0);
            finishReason = AiProviderHttpSupport.text(candidate, "finishReason");
            var contentNode = AiProviderHttpSupport.value(candidate, "content");
            var partsNode = AiProviderHttpSupport.value(contentNode, "parts");
            if (partsNode != null && partsNode.isArray()) {
                for (var position = 0; position < partsNode.size(); position++) {
                    var partNode = partsNode.get(position);
                    if (partNode == null || !partNode.isObject()) {
                        continue;
                    }
                    var functionCall = partNode.get("functionCall");
                    if (functionCall != null && functionCall.isObject()) {
                        if (toolCall == null) {
                            toolCall = streamToolCall(position, functionCall);
                        }
                        continue;
                    }
                    var text = AiProviderHttpSupport.text(partNode, "text");
                    if (text == null) {
                        continue;
                    }
                    var thought = partNode.get("thought");
                    if (thought != null && thought.isBoolean() && thought.booleanValue()) {
                        reasoning = text;
                    } else {
                        content = text;
                    }
                }
            }
        }
        var usage = usage(AiProviderHttpSupport.value(root, "usageMetadata"));
        if (content == null && reasoning == null && toolCall == null && usage == null && finishReason == null) {
            return Optional.empty();
        }
        return Optional.of(new AiStreamEvent(content, reasoning, toolCall, usage, finishReason));
    }

    private AiStreamEvent.ToolCallDelta streamToolCall(int position, JsonNode functionCall) {
        return new AiStreamEvent.ToolCallDelta(
                position,
                null,
                AiProviderHttpSupport.text(functionCall, "name"),
                AiProviderJsonSupport.argumentsText(
                        mapper, AiProviderHttpSupport.value(functionCall, "args"), PROTOCOL));
    }

    private void writeMessage(ArrayNode contents, AiChatMessage message, Map<String, String> toolNames) {
        var role = ASSISTANT_ROLE.equals(message.role()) ? MODEL_ROLE : USER_ROLE;
        var parts = mapper.createArrayNode();
        for (var part : message.parts()) {
            // Gemini 请求不能回传思考内容。
            if (part instanceof AiContentPart.Text text) {
                if (!text.text().isEmpty()) {
                    parts.addObject().put("text", text.text());
                }
            } else if (part instanceof AiContentPart.ToolCall call) {
                var functionCall = parts.addObject().putObject("functionCall");
                if (call.name() != null) {
                    functionCall.put("name", call.name());
                }
                functionCall.set("args", AiProviderJsonSupport.objectArguments(mapper, call.arguments()));
            } else if (part instanceof AiContentPart.ToolResult result) {
                var functionResponse = parts.addObject().putObject("functionResponse");
                functionResponse.put("name", toolName(result.toolCallId(), toolNames));
                functionResponse.set("response", toolResponse(result.content()));
            } else if (part instanceof AiContentPart.Image image) {
                writeImage(parts, image);
            }
        }
        if (parts.isEmpty()) {
            return;
        }
        var content = contents.addObject();
        content.put("role", role);
        content.set("parts", parts);
    }

    private ObjectNode toolResponse(JsonNode content) {
        if (content != null && content.isObject()) {
            return (ObjectNode) content;
        }
        var response = mapper.createObjectNode();
        if (content != null && !content.isNull()) {
            response.set("content", content);
        }
        return response;
    }

    private void writeImage(ArrayNode parts, AiContentPart.Image image) {
        if (image.data() != null) {
            var inlineData = parts.addObject().putObject("inlineData");
            if (image.mediaType() != null) {
                inlineData.put("mimeType", image.mediaType());
            }
            inlineData.put("data", image.data());
            return;
        }
        if (image.url() != null) {
            var fileData = parts.addObject().putObject("fileData");
            if (image.mediaType() != null) {
                fileData.put("mimeType", image.mediaType());
            }
            fileData.put("fileUri", image.url());
        }
    }

    private void writeTools(ArrayNode toolsNode, List<AiTool> tools) {
        var declarations = toolsNode.addObject().putArray("functionDeclarations");
        for (var tool : tools) {
            var declaration = declarations.addObject();
            declaration.put("name", tool.name());
            if (tool.description() != null) {
                declaration.put("description", tool.description());
            }
            if (tool.parameters() != null) {
                declaration.set("parameters", tool.parameters());
            }
        }
    }

    private void addParts(List<AiContentPart> parts, JsonNode partsNode) {
        if (partsNode == null) {
            return;
        }
        if (!partsNode.isArray()) {
            throw new AiProtocolException("Gemini 响应 parts 必须是数组");
        }
        for (var partNode : partsNode) {
            if (partNode == null || !partNode.isObject()) {
                throw new AiProtocolException("Gemini 响应 parts 元素必须是 JSON 对象");
            }
            parts.add(part(partNode));
        }
    }

    private static AiContentPart part(JsonNode partNode) {
        var functionCall = partNode.get("functionCall");
        if (functionCall != null && functionCall.isObject()) {
            return new AiContentPart.ToolCall(
                    null,
                    AiProviderHttpSupport.text(functionCall, "name"),
                    AiProviderHttpSupport.value(functionCall, "args"),
                    null);
        }
        var text = AiProviderHttpSupport.text(partNode, "text");
        if (text != null) {
            var thought = partNode.get("thought");
            if (thought != null && thought.isBoolean() && thought.booleanValue()) {
                return new AiContentPart.Thinking(text, null, null);
            }
            return new AiContentPart.Text(text, null);
        }
        return new AiContentPart.Custom("part", partNode);
    }

    private static JsonNode firstCandidate(JsonNode root) {
        var candidates = root.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            throw new AiProtocolException("Gemini 响应缺少 candidates");
        }
        var candidate = candidates.get(0);
        if (candidate == null || !candidate.isObject()) {
            throw new AiProtocolException("Gemini 响应 candidate 必须是 JSON 对象");
        }
        return candidate;
    }

    private static AiTokenUsage usage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        var input = AiProviderHttpSupport.integer(usageNode, "promptTokenCount");
        var output = AiProviderHttpSupport.integer(usageNode, "candidatesTokenCount");
        var cached = AiProviderHttpSupport.integer(usageNode, "cachedContentTokenCount");
        if (input == null && output == null && cached == null) {
            return null;
        }
        return new AiTokenUsage(input, output, cached, null);
    }

    private static void failOnError(JsonNode root) {
        var error = AiProviderHttpSupport.value(root, "error");
        if (error != null) {
            var message = AiProviderHttpSupport.text(error, "message");
            throw new AiProtocolException("Gemini 错误: " + (message == null ? "未知错误" : message));
        }
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
