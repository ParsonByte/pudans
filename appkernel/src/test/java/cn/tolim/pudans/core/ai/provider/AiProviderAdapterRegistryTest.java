package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiProviderAdapterRegistryTest {

    @Test
    void resolvesAdapterByProtocol() {
        var openAi = adapter(AiProvider.Protocol.OPENAI_CHAT);
        var anthropic = adapter(AiProvider.Protocol.ANTHROPIC);
        var registry = new AiProviderAdapterRegistry(List.of(openAi, anthropic));

        assertThat(registry.require(AiProvider.Protocol.OPENAI_CHAT)).isSameAs(openAi);
        assertThat(registry.require(AiProvider.Protocol.ANTHROPIC)).isSameAs(anthropic);
    }

    @Test
    void rejectsDuplicateProtocols() {
        assertThatThrownBy(() -> new AiProviderAdapterRegistry(List.of(
                        adapter(AiProvider.Protocol.OPENAI_CHAT), adapter(AiProvider.Protocol.OPENAI_CHAT))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPENAI_CHAT");
    }

    @Test
    void rejectsUnknownProtocol() {
        var registry = new AiProviderAdapterRegistry(List.of(adapter(AiProvider.Protocol.OPENAI_CHAT)));

        assertThatThrownBy(() -> registry.require(AiProvider.Protocol.GEMINI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GEMINI");
        assertThatThrownBy(() -> registry.require(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    private static AiProviderAdapter adapter(AiProvider.Protocol protocol) {
        var adapter = mock(AiProviderAdapter.class);
        when(adapter.protocol()).thenReturn(protocol);
        return adapter;
    }
}
