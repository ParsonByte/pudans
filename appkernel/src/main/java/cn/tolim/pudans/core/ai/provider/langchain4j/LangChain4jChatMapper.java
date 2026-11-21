package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public abstract class LangChain4jChatMapper {

    protected static final String THINKING_SIGNATURE_KEY = "thinking_signature";

    protected static final String REDACTED_THINKING_KEY = "redacted_thinking";

    protected static final String REDACTED_THINKING_TYPE = "redacted_thinking";

    protected static final String PDF_MEDIA_TYPE = "application/pdf";

    private static final String ROLE_SYSTEM = "system";

    private static final String ROLE_ASSISTANT = "assistant";

    private static final String ROLE_USER = "user";

    private static final String ROLE_TOOL = "tool";

    protected final ObjectMapper mapper;

    private final Function<TokenUsage, AiTokenUsage> usageMapper;

    protected LangChain4jChatMapper(
            ObjectMapper mapper, Function<TokenUsage, AiTokenUsage> usageMapper) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.usageMapper = Objects.requireNonNull(usageMapper, "TokenUsage 映射器不能为空");
    }

    public String modelCode(@Nullable AiModel model, AiChatIr request) {
        var spec = model == null ? null : model.getSpec();
        var modelCode = spec == null ? null : spec.getModelCode();
        if (!StringUtils.hasText(modelCode)) {
            modelCode = request.requestParams().model();
        }
        if (!StringUtils.hasText(modelCode)) {
            throw new AiProtocolException("模型编码不能为空");
        }
        return modelCode;
    }

    public @Nullable Integer maxTokens(@Nullable AiModel model, AiChatIr request) {
        var requested = request.requestParams().maxTokens();
        if (requested != null) {
            return requested;
        }
        var spec = model == null ? null : model.getSpec();
        return spec == null ? null : spec.getMaxOutputTokens();
    }

    public ChatRequest toRequest(AiChatIr request, String modelCode, @Nullable Integer maxTokens) {
        var toolNames = toolNames(request.messages());
        var messages = messages(request.messages(), toolNames);
        var parameters = parameters(
                request.requestParams(), toolSpecifications(request.tools()), modelCode, maxTokens);
        return ChatRequest.builder().messages(messages).parameters(parameters).build();
    }

    protected abstract ChatRequestParameters parameters(
            AiRequestParams params,
            List<ToolSpecification> tools,
            String modelCode,
            @Nullable Integer maxTokens);

    protected static void commonParameters(
            DefaultChatRequestParameters.Builder builder,
            AiRequestParams params,
            List<ToolSpecification> tools,
            String modelCode,
            @Nullable Integer maxTokens) {
        builder.modelName(modelCode);
        if (params.temperature() != null) {
            builder.temperature(params.temperature());
        }
        if (params.topP() != null) {
            builder.topP(params.topP());
        }
        if (params.topK() != null) {
            builder.topK(params.topK());
        }
        if (maxTokens != null) {
            builder.maxOutputTokens(maxTokens);
        }
        if (!params.stop().isEmpty()) {
            builder.stopSequences(params.stop());
        }
        if (!tools.isEmpty()) {
            builder.toolSpecifications(tools);
        }
    }

    public AiChatResponse toResponse(ChatResponse response) {
        var message = response.aiMessage();
        var parts = new ArrayList<AiContentPart>();
        if (StringUtils.hasText(message.thinking())) {
            parts.add(new AiContentPart.Thinking(
                    message.thinking(), message.attribute(THINKING_SIGNATURE_KEY, String.class), null));
        }
        if (StringUtils.hasText(message.text())) {
            parts.add(new AiContentPart.Text(message.text(), null));
        }
        for (var call : message.toolExecutionRequests()) {
            parts.add(new AiContentPart.ToolCall(
                    call.id(), call.name(), arguments(call.arguments()), null));
        }
        addRedactedThinking(parts, message);
        return new AiChatResponse(
                response.id(),
                response.modelName(),
                new AiChatMessage(ROLE_ASSISTANT, parts),
                usageMapper.apply(response.tokenUsage()),
                finishReason(response.finishReason()));
    }

    public Optional<AiStreamEvent> toStreamEvent(ChatModelStreamingEvent event) {
        if (event instanceof PartialResponse partial) {
            return Optional.of(new AiStreamEvent(partial.text(), null, null, null, null));
        }
        if (event instanceof PartialThinking partial) {
            return Optional.of(new AiStreamEvent(null, partial.text(), null, null, null));
        }
        if (event instanceof PartialToolCall partial) {
            return Optional.of(new AiStreamEvent(
                    null,
                    null,
                    new AiStreamEvent.ToolCallDelta(
                            partial.index(), partial.id(), partial.name(), partial.partialArguments()),
                    null,
                    null));
        }
        if (event instanceof CompleteResponse complete) {
            var response = complete.chatResponse();
            var usage = usageMapper.apply(response.tokenUsage());
            var finishReason = finishReason(response.finishReason());
            if (usage == null && finishReason == null) {
                return Optional.empty();
            }
            return Optional.of(new AiStreamEvent(null, null, null, usage, finishReason));
        }
        return Optional.empty();
    }

    private List<ChatMessage> messages(List<AiChatMessage> source, Map<String, String> toolNames) {
        var result = new ArrayList<ChatMessage>();
        for (var message : source) {
            switch (message.role()) {
                case ROLE_SYSTEM -> addSystem(result, message);
                case ROLE_ASSISTANT -> result.add(assistant(message));
                case ROLE_USER, ROLE_TOOL -> addUser(result, message, toolNames);
                default -> throw new AiProtocolException("不支持消息角色: " + message.role());
            }
        }
        if (result.isEmpty()) {
            throw new AiProtocolException("请求消息不能为空");
        }
        return result;
    }

    private void addSystem(List<ChatMessage> messages, AiChatMessage message) {
        var text = new ArrayList<String>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text content) {
                if (StringUtils.hasText(content.text())) {
                    text.add(content.text());
                }
            } else {
                throw new AiProtocolException("系统消息只支持文本");
            }
        }
        if (!text.isEmpty()) {
            messages.add(SystemMessage.from(String.join("\n\n", text)));
        }
    }

    private AiMessage assistant(AiChatMessage message) {
        var text = new StringBuilder();
        var thinking = new StringBuilder();
        var calls = new ArrayList<ToolExecutionRequest>();
        var attributes = new HashMap<String, Object>();
        var redacted = new ArrayList<String>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text content) {
                text.append(content.text());
            } else if (part instanceof AiContentPart.Thinking content) {
                thinking.append(content.text());
                if (StringUtils.hasText(content.signature())) {
                    attributes.put(THINKING_SIGNATURE_KEY, content.signature());
                }
            } else if (part instanceof AiContentPart.ToolCall call) {
                if (!StringUtils.hasText(call.id()) || !StringUtils.hasText(call.name())) {
                    throw new AiProtocolException("工具调用缺少 id 或名称");
                }
                calls.add(ToolExecutionRequest.builder()
                        .id(call.id())
                        .name(call.name())
                        .arguments(arguments(call.arguments()))
                        .build());
            } else if (part instanceof AiContentPart.Custom custom
                    && REDACTED_THINKING_TYPE.equals(custom.type())) {
                var data = textValue(custom.raw(), "data");
                if (StringUtils.hasText(data)) {
                    redacted.add(data);
                }
            } else {
                throw new AiProtocolException("助手消息不支持该内容类型");
            }
        }
        if (!redacted.isEmpty()) {
            attributes.put(REDACTED_THINKING_KEY, redacted);
        }
        return AiMessage.builder()
                .text(text.isEmpty() ? null : text.toString())
                .thinking(thinking.isEmpty() ? null : thinking.toString())
                .toolExecutionRequests(calls)
                .attributes(attributes.isEmpty() ? null : attributes)
                .build();
    }

    private void addUser(
            List<ChatMessage> messages, AiChatMessage message, Map<String, String> toolNames) {
        var contents = new ArrayList<Content>();
        for (var part : message.parts()) {
            if (part instanceof AiContentPart.Text content) {
                contents.add(TextContent.from(content.text()));
            } else if (part instanceof AiContentPart.Image image) {
                contents.add(image(image));
            } else if (part instanceof AiContentPart.File file) {
                contents.add(file(file));
            } else if (part instanceof AiContentPart.Audio audio) {
                contents.add(audio(audio));
            } else if (part instanceof AiContentPart.Video video) {
                contents.add(video(video));
            } else if (part instanceof AiContentPart.ToolResult result) {
                flushUser(messages, contents);
                messages.add(toolResult(result, toolNames));
            } else {
                throw new AiProtocolException("用户消息不支持该内容类型");
            }
        }
        flushUser(messages, contents);
    }

    private static void flushUser(List<ChatMessage> messages, List<Content> contents) {
        if (!contents.isEmpty()) {
            messages.add(UserMessage.from(contents));
            contents.clear();
        }
    }

    protected Content audio(AiContentPart.Audio audio) {
        if (StringUtils.hasText(audio.data())) {
            return AudioContent.from(audio.data(), mediaType(audio.mediaType(), "audio/mpeg"));
        }
        if (StringUtils.hasText(audio.url())) {
            return AudioContent.from(audio.url());
        }
        throw new AiProtocolException("音频缺少 data 或 url");
    }

    protected Content video(AiContentPart.Video video) {
        if (StringUtils.hasText(video.data())) {
            return VideoContent.from(video.data(), mediaType(video.mediaType(), "video/mp4"));
        }
        if (StringUtils.hasText(video.url())) {
            return VideoContent.from(video.url());
        }
        throw new AiProtocolException("视频缺少 data 或 url");
    }

    protected Content image(AiContentPart.Image image) {
        if (StringUtils.hasText(image.data())) {
            return ImageContent.from(image.data(), mediaType(image.mediaType(), "image/png"));
        }
        if (StringUtils.hasText(image.url())) {
            return ImageContent.from(image.url());
        }
        throw new AiProtocolException("图片缺少 data 或 url");
    }

    protected Content file(AiContentPart.File file) {
        if (!PDF_MEDIA_TYPE.equalsIgnoreCase(file.mediaType())) {
            throw new AiProtocolException("只支持 PDF 文件");
        }
        if (StringUtils.hasText(file.data())) {
            return PdfFileContent.from(file.data(), file.mediaType());
        }
        if (StringUtils.hasText(file.url())) {
            return PdfFileContent.from(file.url());
        }
        throw new AiProtocolException("PDF 文件缺少 data 或 url");
    }

    private static ToolExecutionResultMessage toolResult(
            AiContentPart.ToolResult result, Map<String, String> toolNames) {
        if (!StringUtils.hasText(result.toolCallId())) {
            throw new AiProtocolException("工具结果缺少工具调用 id");
        }
        var toolName = toolNames.get(result.toolCallId());
        if (!StringUtils.hasText(toolName)) {
            toolName = result.toolCallId();
        }
        var builder = ToolExecutionResultMessage.builder()
                .id(result.toolCallId())
                .toolName(toolName)
                .text(resultText(result.content()));
        if (result.error() != null) {
            builder.isError(result.error());
        }
        return builder.build();
    }

    protected final List<ToolSpecification> toolSpecifications(List<AiTool> tools) {
        return LangChain4jToolMapper.toSpecifications(tools);
    }

    private static Map<String, String> toolNames(List<AiChatMessage> messages) {
        var names = new HashMap<String, String>();
        for (var message : messages) {
            if (!ROLE_ASSISTANT.equals(message.role())) {
                continue;
            }
            for (var part : message.parts()) {
                if (part instanceof AiContentPart.ToolCall call
                        && StringUtils.hasText(call.id())
                        && StringUtils.hasText(call.name())) {
                    names.put(call.id(), call.name());
                }
            }
        }
        return names;
    }

    private void addRedactedThinking(List<AiContentPart> parts, AiMessage message) {
        var redacted = message.attribute(REDACTED_THINKING_KEY, List.class);
        if (redacted == null) {
            return;
        }
        for (var item : redacted) {
            if (item == null) {
                continue;
            }
            var raw = mapper.createObjectNode();
            raw.put("type", REDACTED_THINKING_TYPE);
            raw.put("data", String.valueOf(item));
            parts.add(new AiContentPart.Custom(REDACTED_THINKING_TYPE, raw));
        }
    }

    private static @Nullable String finishReason(@Nullable FinishReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case STOP -> "stop";
            case LENGTH -> "length";
            case TOOL_EXECUTION -> "tool_calls";
            case CONTENT_FILTER -> "content_filter";
            case OTHER -> "stop";
        };
    }

    private JsonNode arguments(@Nullable String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return mapper.createObjectNode();
        }
        try {
            var node = mapper.readTree(arguments);
            return node == null ? mapper.createObjectNode() : node;
        } catch (JsonProcessingException error) {
            return mapper.getNodeFactory().textNode(arguments);
        }
    }

    private static String arguments(@Nullable JsonNode arguments) {
        return arguments == null || arguments.isNull() ? "{}" : arguments.toString();
    }

    private static String resultText(@Nullable JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.textValue();
        }
        if (content.isArray()) {
            var text = new ArrayList<String>();
            for (var item : content) {
                if (item.isTextual()) {
                    text.add(item.textValue());
                } else if (item.isObject()) {
                    var value = textValue(item, "text");
                    if (StringUtils.hasText(value)) {
                        text.add(value);
                    }
                }
            }
            if (!text.isEmpty()) {
                return String.join("\n", text);
            }
        }
        return content.toString();
    }

    protected static @Nullable String textValue(@Nullable JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    protected static @Nullable Integer integerValue(@Nullable JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isIntegralNumber() ? value.asInt() : null;
    }

    protected static String mediaType(@Nullable String mediaType, String fallback) {
        return StringUtils.hasText(mediaType) ? mediaType : fallback;
    }
}