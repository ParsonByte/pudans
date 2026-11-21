package cn.tolim.pudans.core.ai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AiModelAdapterRegistryTest {

    @Test
    void resolvesAdapterByProtocolAndCapability() {
        var image = adapter(Set.of(AiProvider.Protocol.OPENAI_CHAT), Set.of(AiModel.Capability.IMAGE_GENERATION));
        var speech = adapter(Set.of(AiProvider.Protocol.OPENAI_CHAT), Set.of(AiModel.Capability.SPEECH_SYNTHESIS));
        var sixD = adapter(Set.of(AiProvider.Protocol.GEMINI), Set.of(AiModel.Capability.THREE_D_GENERATION));
        var registry = new AiModelAdapterRegistry(List.of(image, speech, sixD));

        assertThat(registry.require(AiProvider.Protocol.OPENAI_CHAT, AiModel.Capability.IMAGE_GENERATION))
                .isSameAs(image);
        assertThat(registry.require(AiProvider.Protocol.OPENAI_CHAT, AiModel.Capability.SPEECH_SYNTHESIS))
                .isSameAs(speech);
        assertThat(registry.require(AiProvider.Protocol.GEMINI, AiModel.Capability.THREE_D_GENERATION))
                .isSameAs(sixD);
    }

    @Test
    void reportsSupportedCapabilities() {
        var registry = new AiModelAdapterRegistry(List.of(
                adapter(
                        Set.of(AiProvider.Protocol.ANTHROPIC),
                        Set.of(AiModel.Capability.MUSIC_GENERATION))));

        assertThat(registry.supports(AiProvider.Protocol.ANTHROPIC, AiModel.Capability.MUSIC_GENERATION))
                .isTrue();
        assertThat(registry.supports(AiProvider.Protocol.ANTHROPIC, AiModel.Capability.VIDEO_GENERATION))
                .isFalse();
        assertThat(registry.supports(AiProvider.Protocol.GEMINI, AiModel.Capability.MUSIC_GENERATION))
                .isFalse();
        assertThat(registry.supports(null, AiModel.Capability.MUSIC_GENERATION)).isFalse();
        assertThat(registry.supports(AiProvider.Protocol.ANTHROPIC, null)).isFalse();
    }

    @Test
    void rejectsUnsupportedProtocolCapabilityPairs() {
        var registry = new AiModelAdapterRegistry(List.of(
                adapter(Set.of(AiProvider.Protocol.GEMINI), Set.of(AiModel.Capability.VIDEO_GENERATION))));

        assertThatThrownBy(() ->
                        registry.require(AiProvider.Protocol.GEMINI, AiModel.Capability.THREE_D_GENERATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GEMINI")
                .hasMessageContaining("THREE_D_GENERATION");
    }

    private static AiModelAdapter adapter(
            Set<AiProvider.Protocol> protocols, Set<AiModel.Capability> capabilities) {
        return new AiModelAdapter() {

            @Override
            public Set<AiProvider.Protocol> protocols() {
                return protocols;
            }

            @Override
            public Set<AiModel.Capability> capabilities() {
                return capabilities;
            }

            @Override
            public Mono<AiModelResponse> invoke(
                    AiProvider provider,
                    AiProviderAccount account,
                    AiModel model,
                    AiModelRequest request) {
                return Mono.empty();
            }

            @Override
            public Mono<AiModelResponse> poll(
                    AiProvider provider,
                    AiProviderAccount account,
                    AiModel model,
                    AiModel.Capability capability,
                    String providerTaskId,
                    @Nullable Map<String, ?> payload) {
                return Mono.empty();
            }
        };
    }
}
