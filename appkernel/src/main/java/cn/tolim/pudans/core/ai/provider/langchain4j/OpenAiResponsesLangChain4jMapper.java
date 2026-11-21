package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public final class OpenAiResponsesLangChain4jMapper extends LangChain4jChatMapper {

    public OpenAiResponsesLangChain4jMapper(ObjectMapper mapper) {
        super(mapper, OpenAiResponsesLangChain4jMapper::usage);
    }

    @Override
    protected ChatRequestParameters parameters(
            AiRequestParams params,
            List<ToolSpecification> tools,
            String modelCode,
            @Nullable Integer maxTokens) {
        var builder = OpenAiResponsesChatRequestParameters.builder();
        commonParameters(builder, params, tools, modelCode, maxTokens);
        extras(builder, params.extra());
        toolChoice(builder, params.toolChoice());
        return builder.build();
    }

    private void extras(
            OpenAiResponsesChatRequestParameters.Builder builder,
            Map<String, JsonNode> extras) {
        for (var entry : extras.entrySet()) {
            var value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            switch (entry.getKey()) {
                case "previous_response_id" -> {
                    if (value.isTextual()) {
                        builder.previousResponseId(value.textValue());
                    }
                }
                case "max_tool_calls" -> {
                    if (value.isIntegralNumber()) {
                        builder.maxToolCalls(value.asInt());
                    }
                }
                case "parallel_tool_calls" -> {
                    if (value.isBoolean()) {
                        builder.parallelToolCalls(value.asBoolean());
                    }
                }
                case "top_logprobs" -> {
                    if (value.isIntegralNumber()) {
                        builder.topLogprobs(value.asInt());
                    }
                }
                case "truncation" -> {
                    if (value.isTextual()) {
                        builder.truncation(value.textValue());
                    }
                }
                case "include" -> {
                    if (value.isArray()) {
                        builder.include(textList(value));
                    }
                }
                case "service_tier" -> {
                    if (value.isTextual()) {
                        builder.serviceTier(value.textValue());
                    }
                }
                case "safety_identifier" -> {
                    if (value.isTextual()) {
                        builder.safetyIdentifier(value.textValue());
                    }
                }
                case "prompt_cache_key" -> {
                    if (value.isTextual()) {
                        builder.promptCacheKey(value.textValue());
                    }
                }
                case "prompt_cache_retention" -> {
                    if (value.isTextual()) {
                        builder.promptCacheRetention(value.textValue());
                    }
                }
                case "reasoning_effort" -> {
                    if (value.isTextual()) {
                        builder.reasoningEffort(value.textValue());
                    }
                }
                case "reasoning_summary" -> {
                    if (value.isTextual()) {
                        builder.reasoningSummary(value.textValue());
                    }
                }
                case "text_verbosity" -> {
                    if (value.isTextual()) {
                        builder.textVerbosity(value.textValue());
                    }
                }
                case "stream_include_obfuscation" -> {
                    if (value.isBoolean()) {
                        builder.streamIncludeObfuscation(value.asBoolean());
                    }
                }
                case "store" -> {
                    if (value.isBoolean()) {
                        builder.store(value.asBoolean());
                    }
                }
                case "strict_tools" -> {
                    if (value.isBoolean()) {
                        builder.strictTools(value.asBoolean());
                    }
                }
                case "strict_json_schema" -> {
                    if (value.isBoolean()) {
                        builder.strictJsonSchema(value.asBoolean());
                    }
                }
                case "server_tools" -> {
                    if (value.isArray()) {
                        builder.serverTools(objectList(value));
                    }
                }
                default -> {
                    // 未映射字段在本层忽略。
                }
            }
        }
    }

    private static void toolChoice(
            OpenAiResponsesChatRequestParameters.Builder builder, @Nullable JsonNode choice) {
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
        builder.toolChoice(ToolChoice.REQUIRED);
    }

    private static List<String> textList(JsonNode node) {
        var values = new ArrayList<String>();
        for (var item : node) {
            if (item.isTextual()) {
                values.add(item.textValue());
            }
        }
        return values;
    }

    private List<Map<String, Object>> objectList(JsonNode node) {
        var values = new ArrayList<Map<String, Object>>();
        for (var item : node) {
            if (item.isObject()) {
                values.add(mapper.convertValue(item, Map.class));
            }
        }
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