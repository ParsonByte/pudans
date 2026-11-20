package cn.tolim.pudans.core.ai.provider.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatRequestParameters;

public final class GeminiLangChain4jChatMapper extends SimpleLangChain4jChatMapper {

    public GeminiLangChain4jChatMapper(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected DefaultChatRequestParameters.Builder<?> parametersBuilder() {
        return GoogleAiGeminiChatRequestParameters.builder();
    }
}