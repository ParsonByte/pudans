package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiProvidersTest {

    @Mock
    AiProviderHttp http;

    @Test
    void resolvesConfiguredProvidersWithoutADedicatedBean() {
        var providers = new AiProviders(List.of(), http);

        var provider = providers.require("custom");

        assertThat(provider.id()).isEqualTo("custom");
        assertThat(provider.descriptor().defaultProtocol()).isEqualTo("chat-completions");
        assertThat(provider.resolveBaseUrl("https://api.example.com/v1"))
                .isEqualTo("https://api.example.com/v1");
    }

}