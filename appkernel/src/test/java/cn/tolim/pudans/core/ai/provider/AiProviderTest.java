package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AiProviderTest {

    private final AiProvider provider = new AiProvider() {

        @Override
        public AiProviderDescriptor descriptor() {
            return new AiProviderDescriptor(
                    "custom",
                    "Custom",
                    null,
                    null,
                    null,
                    "https://api.example.com",
                    "messages",
                    List.of("messages"),
                    List.of("chat"),
                    List.of(),
                    true,
                    List.of());
        }

        @Override
        public Mono<List<AiProviderModel>> discover(
                AiChannelEntity channel, Map<String, String> credentials) {
            return Mono.just(List.of());
        }
    };

    @Test
    void appendsTheVersionSegmentExpectedByTheAnthropicSdk() {
        assertThat(provider.anthropicBaseUrl("https://api.example.com/apps/anthropic"))
                .isEqualTo("https://api.example.com/apps/anthropic/v1");
    }

    @Test
    void keepsAnAlreadyVersionedAnthropicBaseUrl() {
        assertThat(provider.anthropicBaseUrl("https://api.example.com/v1"))
                .isEqualTo("https://api.example.com/v1");
    }
}