package cn.tolim.pudans.core.ai.provider.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;

public final class OllamaLangChain4jChatMapper extends SimpleLangChain4jChatMapper {

    public OllamaLangChain4jChatMapper(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected DefaultChatRequestParameters.Builder<?> parametersBuilder() {
        return OllamaChatRequestParameters.builder();
    }
}