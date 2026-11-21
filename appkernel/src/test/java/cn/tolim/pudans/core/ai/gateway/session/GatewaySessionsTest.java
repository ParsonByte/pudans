package cn.tolim.pudans.core.ai.gateway.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.NewGatewaySession;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GatewaySessionsTest {

    @Mock
    AiGatewaySessionRepository repository;

    @Mock
    Teams teamService;

    @Mock
    AiSettings settingService;

    private GatewaySessions service;

    @BeforeEach
    void setUp() {
        service = new GatewaySessions(repository, teamService, settingService);
    }

    @Test
    void bindsTheAgentTeamAndName() {
        when(teamService.requireAgent("agent-a", "user-1")).thenReturn(Mono.just(agent("agent-a", "team-a")));
        when(repository.upsert(any())).thenAnswer(invocation -> Mono.just(session(invocation.getArgument(0))));

        StepVerifier.create(service.bind(new GatewaySessions.BindRequest(
                        "user-1", "claude-code", "session-1", "native-1", "agent-a", "task-1", 11L)))
                .assertNext(found -> {
                    assertThat(found.teamId()).isEqualTo("team-a");
                    assertThat(found.agentId()).isEqualTo("agent-a");
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(NewGatewaySession.class);
        org.mockito.Mockito.verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().teamId()).isEqualTo("team-a");
    }

    @Test
    void usesTheConfiguredDefaultAgent() {
        var defaults = new AiSetting.Defaults();
        defaults.setAgent("default");
        when(settingService.defaults()).thenReturn(Mono.just(defaults));
        when(teamService.requireAgent("default", "user-1")).thenReturn(Mono.just(agent("default", "default")));
        when(repository.upsert(any())).thenAnswer(invocation -> Mono.just(session(invocation.getArgument(0))));

        StepVerifier.create(service.bind(new GatewaySessions.BindRequest(
                        "user-1", "openai-default", "session-1", null, null, null, null)))
                .assertNext(found -> assertThat(found.agentId()).isEqualTo("default"))
                .verifyComplete();
    }

    @Test
    void requireSessionFailsWhenNoBindingExists() {
        when(repository.findBySession("default", "user-1", "claude-code", "session-1"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.requireSession("user-1", "claude-code", "session-1"))
                .expectError(ApiException.class)
                .verify();
    }

    @Test
    void unbindsBySession() {
        when(repository.deleteBySession("default", "user-1", "claude-code", "session-1"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.unbind("user-1", "claude-code", "session-1"))
                .expectNext(true)
                .verifyComplete();
    }

    private static AiAgent agent(String name, String team) {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName(name);
        agent.setMetadata(metadata);
        agent.getSpec().setDisplayName(name);
        agent.getSpec().setTeam(team);
        agent.getSpec().setOwnerUserId("system");
        return agent;
    }

    private static GatewaySession session(NewGatewaySession newSession) {
        return new GatewaySession(
                1L,
                newSession.spaceId(),
                newSession.userId(),
                newSession.agentSource(),
                newSession.sessionKey(),
                newSession.sessionId(),
                newSession.teamId(),
                newSession.agentId(),
                newSession.taskId(),
                newSession.conversationId(),
                "ACTIVE",
                Instant.now(),
                Instant.now());
    }
}
