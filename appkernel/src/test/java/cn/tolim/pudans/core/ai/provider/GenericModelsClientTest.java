package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import org.junit.jupiter.api.Test;

class GenericModelsClientTest {

    @Test
    void supportsOpenAiAnthropicAndGeminiCompatibleProtocols() {
        assertThat(GenericModelsClient.supports(AiProvider.Protocol.OPENAI_CHAT)).isTrue();
        assertThat(GenericModelsClient.supports(AiProvider.Protocol.OPENAI_RESPONSES)).isTrue();
        assertThat(GenericModelsClient.supports(AiProvider.Protocol.ANTHROPIC)).isTrue();
        assertThat(GenericModelsClient.supports(AiProvider.Protocol.GEMINI_OPENAI)).isTrue();
        assertThat(GenericModelsClient.supports(AiProvider.Protocol.DEEPSEEK)).isFalse();
        assertThat(GenericModelsClient.supports(AiProvider.Protocol.GEMINI)).isFalse();
        assertThat(GenericModelsClient.supports(AiProvider.Protocol.OLLAMA)).isFalse();
    }

    @Test
    void usesGeminiOpenAiPathForGeminiCompatible() {
        assertThat(GenericModelsClient.path(AiProvider.Protocol.OPENAI_CHAT)).isEqualTo("/v1/models");
        assertThat(GenericModelsClient.path(AiProvider.Protocol.ANTHROPIC)).isEqualTo("/v1/models");
        assertThat(GenericModelsClient.path(AiProvider.Protocol.GEMINI_OPENAI)).isEqualTo("/models");
    }
}
