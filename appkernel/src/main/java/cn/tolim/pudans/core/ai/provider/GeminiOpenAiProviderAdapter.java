package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiOpenAiProviderAdapter extends OpenAiChatProviderAdapter {

    private static final String CHAT_PATH = "/chat/completions";

    public GeminiOpenAiProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, mapper, accountService);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.GEMINI_OPENAI;
    }

    @Override
    protected String path() {
        return CHAT_PATH;
    }
}
