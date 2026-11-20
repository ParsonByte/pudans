package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public final class AnthropicLangChain4jMapper extends LangChain4jChatMapper {

    public AnthropicLangChain4jMapper(ObjectMapper mapper) {
        super(mapper, AnthropicLangChain4jMapper::usage);
    }

    @Override
    protected ChatRequestParameters parameters(
            AiRequestParams params,
            List<ToolSpecification> tools,
            String modelCode,
            @Nullable Integer maxTokens) {
        var builder = AnthropicChatRequestParameters.builder();
        commonParameters(builder, params, tools, modelCode, maxTokens);
        toolChoice(builder, params.toolChoice());
        extras(builder, params.extra());
        return builder.build();
    }

    private static void toolChoice(
            AnthropicChatRequestParameters.Builder builder, @Nullable JsonNode choice) {
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
        var name = textValue(choice, "name");
        if (!StringUtils.hasText(name)) {
            name = textValue(choice.get("function"), "name");
        }
        if (!StringUtils.hasText(name)) {
            throw new AiProtocolException("tool_choice 缺少工具名称");
        }
        builder.toolChoiceName(name);
    }

    private static void extras(
            AnthropicChatRequestParameters.Builder builder, Map<String, JsonNode> extras) {
        var thinking = extras.get("thinking");
        if (thinking != null && thinking.isObject()) {
            var type = textValue(thinking, "type");
            if (StringUtils.hasText(type)) {
                builder.thinkingType(type);
            }
            var budget = integerValue(thinking, "budget_tokens");
            if (budget != null) {
                builder.thinkingBudgetTokens(budget);
            }
        }
        var metadata = extras.get("metadata");
        if (metadata != null && metadata.isObject()) {
            var userId = textValue(metadata, "user_id");
            if (StringUtils.hasText(userId)) {
                builder.userId(userId);
            }
        }
    }

    private static AiTokenUsage usage(@Nullable TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        if (usage instanceof AnthropicTokenUsage anthropic) {
            return new AiTokenUsage(
                    anthropic.inputTokenCount(),
                    anthropic.outputTokenCount(),
                    anthropic.cacheReadInputTokens(),
                    anthropic.cacheCreationInputTokens());
        }
        return new AiTokenUsage(usage.inputTokenCount(), usage.outputTokenCount(), null, null);
    }
}