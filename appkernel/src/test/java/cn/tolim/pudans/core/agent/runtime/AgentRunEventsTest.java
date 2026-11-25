package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.AgentRun;
import cn.tolim.pudans.core.creation.task.AiEventStreams;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AgentRunEventsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    AiEventStreams streams;

    private AgentRunEvents events;

    @BeforeEach
    void setUp() {
        events = new AgentRunEvents(
                streams, Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesAnOrderedRunEventWithIdentityData() {
        var runId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        when(streams.nextSequence(runId.toString())).thenReturn(Mono.just(7L));
        when(streams.append(eq(runId.toString()), anyString())).thenReturn(Mono.just("1-7"));

        StepVerifier.create(events.publish(
                        runId,
                        sessionId,
                        AgentRunEvents.RUN_QUEUED,
                        Map.of("status", "queued")))
                .verifyComplete();

        var payload = ArgumentCaptor.forClass(String.class);
        verify(streams).append(eq(runId.toString()), payload.capture());
        assertThat(payload.getValue())
                .contains("\"seq\":7")
                .contains("\"runId\":\"" + runId + "\"")
                .contains("\"sessionId\":\"" + sessionId + "\"")
                .contains("\"type\":\"run.queued\"");
    }

    @Test
    void replaysStoredEventsWithTheirRedisIds() {
        var run = run("running");
        when(streams.replay(run.id().toString(), "0-9"))
                .thenReturn(Flux.just(new AiEventStreams.StoredEvent(
                        "1-0", payload(run, 1, AgentRunEvents.RUN_STARTED))));
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(run, "0-9").take(1))
                .assertNext(event -> {
                    assertThat(event.eventId()).isEqualTo("1-0");
                    assertThat(event.seq()).isEqualTo(1);
                    assertThat(event.type()).isEqualTo(AgentRunEvents.RUN_STARTED);
                })
                .verifyComplete();
    }

    @Test
    void emitsTheDurableTerminalStateWhenReplayExpired() {
        var run = run("completed");
        when(streams.replay(run.id().toString(), null)).thenReturn(Flux.empty());
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(run, null))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(AgentRunEvents.RUN_COMPLETED);
                    assertThat(event.data())
                            .containsEntry("fallback", true)
                            .containsEntry("status", "completed");
                    assertThat(event.eventId()).startsWith("fallback-");
                })
                .verifyComplete();
    }

    @Test
    void rejectsMalformedLastEventIdsBeforeReadingRedis() {
        StepVerifier.create(events.stream(run("running"), "not-a-stream-id"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(400);
                            assertThat(api.getCode()).isEqualTo("agent-event-id-invalid");
                        }))
                .verify();

        verifyNoInteractions(streams);
    }

    @Test
    void emitsKeepalivePingsWithoutAStreamId() {
        var run = run("running");
        var keepalives = new AgentRunEvents(
                streams, Duration.ofMillis(10), Clock.fixed(NOW, ZoneOffset.UTC));
        when(streams.replay(run.id().toString(), null)).thenReturn(Flux.empty());
        when(streams.live()).thenReturn(Flux.never());

        StepVerifier.create(keepalives.stream(run, null).take(1))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(AgentRunEvents.PING);
                    assertThat(event.eventId()).isNull();
                    assertThat(event.seq()).isZero();
                })
                .verifyComplete();
    }

    private static String payload(AgentRun run, long seq, String type) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("seq", seq);
        payload.put("runId", run.id());
        payload.put("sessionId", run.sessionId());
        payload.put("type", type);
        payload.put("data", Map.of("status", run.status()));
        payload.put("createdAt", NOW.toString());
        return JSON.writeValueAsString(payload);
    }

    private static AgentRun run(String status) {
        var terminal = status.equals("completed") || status.equals("failed") || status.equals("cancelled");
        return new AgentRun(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                status,
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                null,
                status.equals("running"),
                false,
                null,
                null,
                NOW,
                status.equals("queued") ? null : NOW,
                terminal ? NOW : null);
    }
}
