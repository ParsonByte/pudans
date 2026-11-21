package cn.tolim.pudans.core.ai.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import org.junit.jupiter.api.Test;

class LangChain4jModelFactoryTest {

    private final LangChain4jModelFactory subject = new LangChain4jModelFactory();

    @Test
    void mapsProtocolBasePaths() {
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.OPENAI_CHAT)).isEqualTo("/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.OPENAI_RESPONSES)).isEqualTo("/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.ANTHROPIC)).isEqualTo("/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.GEMINI_OPENAI)).isEmpty();
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.QIANWEN))
                .isEqualTo("/compatible-mode/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.CUSTOM_HTTP)).isEqualTo("/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.DEEPSEEK))
                .isEqualTo("/anthropic/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.DEEPSEEK_CHAT)).isEmpty();
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.DEEPSEEK_RESPONSES)).isEmpty();
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.MINIMAX))
                .isEqualTo("/anthropic/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.MINIMAX_CHAT)).isEqualTo("/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.MINIMAX_RESPONSES))
                .isEqualTo("/v1");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.GEMINI)).isEqualTo("/v1beta");
        assertThat(LangChain4jModelFactory.basePath(AiProvider.Protocol.OLLAMA)).isEmpty();
    }

    @Test
    void buildsChatModelsForEverySupportedProtocol() {
        for (var protocol : AiProvider.Protocol.values()) {
            if (protocol == AiProvider.Protocol.MINIMAX_NATIVE) {
                continue;
            }
            assertThat(subject.chatModel(provider(protocol), protocol, "model-x", "key"))
                    .as(protocol.toString())
                    .isNotNull();
            assertThat(subject.streamingChatModel(provider(protocol), protocol, "model-x", "key"))
                    .as(protocol.toString())
                    .isNotNull();
        }
    }

    @Test
    void rejectsMiniMaxNative() {
        assertThatThrownBy(() -> subject.chatModel(
                        provider(AiProvider.Protocol.MINIMAX_NATIVE),
                        AiProvider.Protocol.MINIMAX_NATIVE,
                        "model-x",
                        "key"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("MiniMax Native");
    }

    private static AiProvider provider(AiProvider.Protocol protocol) {
        var provider = new AiProvider();
        provider.getSpec().setDisplayName("upstream");
        provider.getSpec().setProtocol(protocol);
        provider.getSpec().setBaseUrl("https://api.example.com");
        return provider;
    }
}