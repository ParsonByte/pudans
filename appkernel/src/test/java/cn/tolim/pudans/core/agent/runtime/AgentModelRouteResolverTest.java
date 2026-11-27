package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.core.ai.AiModelDirectory;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentModelRouteResolverTest {

    @Mock
    AiModelDirectory models;

    @Mock
    AiChannelModelRepository channelModels;

    @Mock
    AiChannels channels;

    @InjectMocks
    AgentModelRouteResolver resolver;

    @Test
    void rejectsAModelWithoutAnEnabledWorkspaceChannel() {
        var model = new AiModel(
                UUID.randomUUID(),
                "chat-default",
                "Chat default",
                null,
                Map.of("chat", true),
                8192,
                1024,
                Map.of(),
                "active",
                Instant.now(),
                Instant.now());
        when(models.resolve(eq("workspace-a"), anyString(), eq("chat")))
                .thenReturn(Mono.just(model));
        when(channelModels.findEnabledRoute(model.id()))
                .thenReturn(Mono.empty());

        StepVerifier.create(resolver.resolve("workspace-a", model.id()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("agent-model-channel-unavailable")))
                .verify();
    }
}
