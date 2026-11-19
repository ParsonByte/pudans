package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class GeminiOpenAiProviderAdapterTest {

    @Test
    void usesGeminiOpenAiCompatibleChatPath() {
        var adapter = new GeminiOpenAiProviderAdapter(
                mock(WebClient.class), new ObjectMapper(), mock(AiProviderAccountService.class));

        assertThat(adapter.protocol()).isEqualTo(AiProvider.Protocol.GEMINI_OPENAI);
        assertThat(adapter.path()).isEqualTo("/chat/completions");
    }
}
