package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.jspecify.annotations.Nullable;

public abstract class SimpleLangChain4jChatMapper extends LangChain4jChatMapper {

    protected SimpleLangChain4jChatMapper(ObjectMapper mapper) {
        super(mapper, SimpleLangChain4jChatMapper::usage);
    }

    @Override
    protected ChatRequestParameters parameters(
            AiRequestParams params,
            List<ToolSpecification> tools,
            String modelCode,
            @Nullable Integer maxTokens) {
        var builder = parametersBuilder();
        commonParameters(builder, params, tools, modelCode, maxTokens);
        toolChoice(builder, params.toolChoice());
        return builder.build();
    }

    protected abstract DefaultChatRequestParameters.Builder<?> parametersBuilder();

    private static void toolChoice(
            DefaultChatRequestParameters.Builder<?> builder, @Nullable JsonNode choice) {
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

    private static AiTokenUsage usage(@Nullable TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return new AiTokenUsage(usage.inputTokenCount(), usage.outputTokenCount(), null, null);
    }
}