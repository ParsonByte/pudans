package cn.tolim.pudans.core.agent.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.AgentRun;
import cn.tolim.pudans.contract.agent.dto.AgentRunEvent;
import cn.tolim.pudans.core.agent.runtime.AgentRunEvents;
import cn.tolim.pudans.core.agent.runtime.AgentRuns;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AgentRunEndpointTest {

    @Mock
    AgentRuns runs;

    @Mock
    AgentRunEvents events;

    @Mock
    ReactiveRequestIdentity identity;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AgentRunEndpoint(runs, events, identity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void startsARunWithTheAuthenticatedRoles() {
        var runId = UUID.randomUUID();
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(identity.roles()).thenReturn(Mono.just(Set.of("agent-user")));
        when(runs.start(any(), any(), any())).thenReturn(Mono.just(run(runId)));

        client.post()
                .uri("/agentruns/-/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"sessionId":"%s","content":"hello"}
                        """.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("queued");

        verify(runs).start(
                eq(new RequestIdentity("alice", "workspace-a")),
                eq(Set.of("agent-user")),
                any());
    }

    @Test
    void returnsTheDurableRunStatus() {
        var runId = UUID.randomUUID();
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(runs.status(any(), any())).thenReturn(Mono.just(run(runId)));

        client.get()
                .uri("/agentruns/{id}/status", runId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("queued");
    }

    @Test
    void streamsReplayedEventsWithTheRedisId() {
        var runId = UUID.randomUUID();
        var run = run(runId);
        var event = new AgentRunEvent(
                "1700000000000-0",
                3,
                runId,
                run.sessionId(),
                AgentRunEvents.RUN_STARTED,
                Map.of("runId", runId, "status", "running"),
                Instant.now());
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(runs.status(any(), eq(runId))).thenReturn(Mono.just(run));
        when(events.stream(run, "1600000000000-0")).thenReturn(Flux.just(event));

        client.get()
                .uri("/agentruns/{id}/events", runId)
                .header("Last-Event-ID", "1600000000000-0")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectHeader()
                .valueEquals("Cache-Control", "no-cache")
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("id:1700000000000-0")
                        .contains("event:run.started")
                        .contains("\"type\":\"run.started\""));
    }

    @Test
    void rejectsAMalformedLastEventIdBeforeResolvingIdentity() {
        client.get()
                .uri("/agentruns/{id}/events", UUID.randomUUID())
                .header("Last-Event-ID", "not-a-stream-id")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, runs, events);
    }

    private static AgentRun run(UUID runId) {
        return new AgentRun(
                runId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "queued",
                null,
                null,
                null,
                List.of(),
                null,
                false,
                false,
                null,
                null,
                Instant.now(),
                null,
                null);
    }
}
