package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiChannel;
import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolDescriptor;
import cn.tolim.pudans.core.ai.protocol.AiProtocols;
import cn.tolim.pudans.core.ai.protocol.chatcompletions.ChatCompletionsProfile;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import io.agentscope.core.model.Model;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentChatModelFactoryTest {

    @Mock
    AiProtocols protocols;

    @Mock
    AiProviders providers;

    @Mock
    AiProvider provider;

    @Mock
    AiChatProtocol chatProtocol;

    @Mock
    Model model;

    private AgentChatModelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AgentChatModelFactory(protocols, providers);
    }

    @Test
    void appliesChatCompletionsReasoningBodyParamsFromProviderProfile() {
        var descriptor = mock(AiProtocolDescriptor.class);
        when(descriptor.protocol()).thenReturn(AiProtocol.CHAT_COMPLETIONS);
        when(descriptor.runtimeSupported()).thenReturn(true);
        when(protocols.find("chat-completions")).thenReturn(Optional.of(descriptor));
        when(protocols.requireChat("chat-completions")).thenReturn(chatProtocol);
        when(providers.require("custom")).thenReturn(provider);
        when(provider.id()).thenReturn("custom");
        when(provider.chatCompletionsBaseUrl("https://api.example.com"))
                .thenReturn("https://api.example.com");
        var profile = new ChatCompletionsProfile() {

            @Override
            public Map<String, Object> reasoningBodyParams(
                    boolean reasoningSupported, String reasoning) {
                return reasoningSupported && reasoning != null
                        ? Map.of("enable_thinking", !"none".equals(reasoning))
                        : Map.of();
            }
        };
        when(provider.protocolProfile(AiProtocol.CHAT_COMPLETIONS)).thenReturn(profile);
        when(chatProtocol.create(any(AiChatProtocolRequest.class), eq(profile)))
                .thenReturn(model);

        var result = factory.create(route(), "high");

        assertThat(result).isSameAs(model);
        var requestCaptor = ArgumentCaptor.forClass(AiChatProtocolRequest.class);
        verify(chatProtocol).create(requestCaptor.capture(), eq(profile));
        assertThat(requestCaptor.getValue().additionalBodyParams())
                .containsEntry("enable_thinking", true);
    }

    private static AgentModelRoute route() {
        var modelId = UUID.randomUUID();
        var channelId = UUID.randomUUID();
        var model = new AiModel(
                modelId,
                "custom/model",
                "Custom",
                null,
                Map.of("chat", Map.of("reasoning", true)),
                32_000,
                4_096,
                Map.of(),
                "active",
                null,
                null);
        var channelModel = new AiChannelModel(
                UUID.randomUUID(),
                channelId,
                modelId,
                "upstream-model",
                100,
                100,
                true,
                Map.of(),
                Map.of(),
                null,
                null);
        var channel = new AiChannel(
                channelId,
                "workspace",
                "custom",
                "chat-completions",
                "https://api.example.com",
                null,
                true,
                Map.of(),
                "active",
                30_000,
                10,
                100,
                null,
                Map.of(),
                null,
                null);
        return new AgentModelRoute(model, channelModel, channel, Map.of("apiKey", "secret"));
    }
}
