package cn.tolim.pudans.core.ai.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class LangChain4jScoringModelFactoryTest {

    private final LangChain4jScoringModelFactory factory =
            new LangChain4jScoringModelFactory(WebClient.builder().build(), new ObjectMapper());

    @Test
    void supportsQianwenAndOpenAiCompatibleRerank() {
        assertThat(factory.supports(AiProvider.Protocol.QIANWEN)).isTrue();
        assertThat(factory.supports(AiProvider.Protocol.OPENAI_CHAT)).isTrue();
        assertThat(factory.supports(AiProvider.Protocol.ANTHROPIC)).isFalse();
        assertThat(factory.supports(null)).isFalse();
    }

    @Test
    void normalizesQianwenCompatibleBaseUrlsToTheApiRoot() {
        assertThat(LangChain4jScoringModelFactory.qwenBaseUrl("https://dashscope.aliyuncs.com"))
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(LangChain4jScoringModelFactory.qwenBaseUrl(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/"))
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        assertThat(LangChain4jScoringModelFactory.qwenBaseUrl(
                        "https://dashscope.aliyuncs.com/api/v1"))
                .isEqualTo("https://dashscope.aliyuncs.com/api/v1");
    }
}