package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public final class OpenAiLangChain4jMapper extends LangChain4jChatMapper {

    public OpenAiLangChain4jMapper(ObjectMapper mapper) {
        super(mapper, OpenAiLangChain4jMapper::usage);
    }

    @Override
    protected ChatRequestParameters parameters(
            AiRequestParams params,
            List<ToolSpecification> tools,
            String modelCode,
            @Nullable Integer maxTokens) {
        var builder = OpenAiChatRequestParameters.builder();
        commonParameters(builder, params, tools, modelCode, maxTokens);
        var custom = new LinkedHashMap<String, Object>();
        extras(builder, params.extra(), custom);
        toolChoice(builder, params.toolChoice(), custom);
        if (!custom.isEmpty()) {
            builder.customParameters(custom);
        }
        return builder.build();
    }

    @Override
    protected Content file(AiContentPart.File file) {
        var mediaType = file.mediaType() == null
                ? ""
                : file.mediaType().toLowerCase(Locale.ROOT);
        if (mediaType.startsWith("image/")) {
            return StringUtils.hasText(file.data())
                    ? ImageContent.from(file.data(), mediaType)
                    : ImageContent.from(fileUrl(file));
        }
        if (mediaType.startsWith("video/")) {
            return StringUtils.hasText(file.data())
                    ? VideoContent.from(file.data(), mediaType)
                    : VideoContent.from(fileUrl(file));
        }
        if (mediaType.startsWith("audio/")) {
            return StringUtils.hasText(file.data())
                    ? AudioContent.from(file.data(), mediaType)
                    : AudioContent.from(fileUrl(file));
        }
        return super.file(file);
    }

    private static String fileUrl(AiContentPart.File file) {
        if (StringUtils.hasText(file.url())) {
            return file.url();
        }
        throw new AiProtocolException("文件缺少 data 或 url");
    }

    private void extras(
            OpenAiChatRequestParameters.Builder builder,
            Map<String, JsonNode> extras,
            Map<String, Object> custom) {
        for (var entry : extras.entrySet()) {
            var value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            switch (entry.getKey()) {
                case "parallel_tool_calls" -> {
                    if (value.isBoolean()) {
                        builder.parallelToolCalls(value.asBoolean());
                    }
                }
                case "seed" -> {
                    if (value.isIntegralNumber()) {
                        builder.seed(value.asInt());
                    }
                }
                case "user" -> {
                    if (value.isTextual()) {
                        builder.user(value.textValue());
                    }
                }
                case "store" -> {
                    if (value.isBoolean()) {
                        builder.store(value.asBoolean());
                    }
                }
                case "metadata" -> {
                    if (value.isObject()) {
                        builder.metadata(textMap(value));
                    }
                }
                case "service_tier" -> {
                    if (value.isTextual()) {
                        builder.serviceTier(value.textValue());
                    }
                }
                case "reasoning_effort" -> {
                    if (value.isTextual()) {
                        builder.reasoningEffort(value.textValue());
                    }
                }
                case "logprobs" -> {
                    if (value.isBoolean()) {
                        builder.logprobs(value.asBoolean());
                    }
                }
                case "top_logprobs" -> {
                    if (value.isIntegralNumber()) {
                        builder.topLogprobs(value.asInt());
                    }
                }
                case "max_completion_tokens" -> {
                    if (value.isIntegralNumber()) {
                        builder.maxCompletionTokens(value.asInt());
                    }
                }
                default -> custom.put(entry.getKey(), mapper.convertValue(value, Object.class));
            }
        }
    }

    private void toolChoice(
            OpenAiChatRequestParameters.Builder builder,
            @Nullable JsonNode choice,
            Map<String, Object> custom) {
        if (choice == null || choice.isNull()) {
            return;
        }
        if (choice.isTextual()) {
            switch (choice.textValue()) {
                case "auto" -> builder.toolChoice(ToolChoice.AUTO);
                case "any", "required" -> builder.toolChoice(ToolChoice.REQUIRED);
                case "none" -> builder.toolChoice(ToolChoice.NONE);
                default -> throw new AiProtocolException(
                        "不支持的 tool_choice: " + choice.textValue());
            }
            return;
        }
        if (!choice.isObject()) {
            throw new AiProtocolException("tool_choice 必须是字符串或 JSON 对象");
        }
        custom.put("tool_choice", mapper.convertValue(choice, Object.class));
    }

    private static Map<String, String> textMap(JsonNode node) {
        var values = new LinkedHashMap<String, String>();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().isTextual()) {
                values.put(entry.getKey(), entry.getValue().textValue());
            }
        });
        return values;
    }

    private static AiTokenUsage usage(@Nullable TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        if (usage instanceof OpenAiTokenUsage openai) {
            var details = openai.inputTokensDetails();
            var cached = details == null ? null : details.cachedTokens();
            // OpenAI 的 prompt_tokens 已包含缓存读取，计费与命中率需要使用未缓存输入。
            var input = cached == null
                    ? openai.inputTokenCount()
                    : Math.max(0, openai.inputTokenCount() - cached);
            return new AiTokenUsage(
                    input,
                    openai.outputTokenCount(),
                    cached,
                    details == null ? null : details.cacheWriteTokens());
        }
        return new AiTokenUsage(usage.inputTokenCount(), usage.outputTokenCount(), null, null);
    }
}