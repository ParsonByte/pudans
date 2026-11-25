package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.core.creation.task.AiEventStreams;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SandboxEventsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    AiEventStreams streams;

    private SandboxEvents events;

    @BeforeEach
    void setUp() {
        events = new SandboxEvents(
                streams, Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesASandboxEventWithTheRedisSequence() {
        var sandbox = sandbox(Sandbox.Status.READY);
        when(streams.nextSequence(sandbox.id().toString())).thenReturn(Mono.just(7L));
        when(streams.append(eq(sandbox.id().toString()), anyString()))
                .thenReturn(Mono.just("1-7"));

        StepVerifier.create(events.publish(
                        sandbox.id(), SandboxEvents.READY, Map.of("status", sandbox.status())))
                .verifyComplete();

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(streams)
                .append(eq(sandbox.id().toString()), payload.capture());
        assertThat(payload.getValue())
                .contains("\"seq\":7")
                .contains("\"sandboxId\":\"" + sandbox.id() + "\"")
                .contains("\"type\":\"sandbox.ready\"");
    }

    @Test
    void replaysStoredEventsWithTheirRedisIds() {
        var sandbox = sandbox(Sandbox.Status.RUNNING);
        when(streams.replay(sandbox.id().toString(), "0-9"))
                .thenReturn(Flux.just(new AiEventStreams.StoredEvent(
                        "1-0",
                        payload(
                                sandbox.id(),
                                1,
                                SandboxEvents.OPERATION_STARTED,
                                Map.of("operationId", UUID.randomUUID())))));
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(sandbox, "0-9").take(1))
                .assertNext(event -> {
                    assertThat(event.eventId()).isEqualTo("1-0");
                    assertThat(event.seq()).isEqualTo(1);
                    assertThat(event.type()).isEqualTo(SandboxEvents.OPERATION_STARTED);
                })
                .verifyComplete();
    }

    @Test
    void emitsTheRealStateWhenReplayHasExpiredAndTheSandboxIsTerminal() {
        var sandbox = sandbox(Sandbox.Status.FAILED);
        when(streams.replay(sandbox.id().toString(), null)).thenReturn(Flux.empty());
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(sandbox, null))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(SandboxEvents.FAILED);
                    assertThat(event.data())
                            .containsEntry("fallback", true)
                            .containsEntry("status", "failed");
                    assertThat(event.eventId()).startsWith("fallback-");
                })
                .verifyComplete();
    }

    @Test
    void appendsTheRealFinalStateWhenReplayHasNoTerminalEvent() {
        var sandbox = sandbox(Sandbox.Status.STOPPED);
        when(streams.replay(sandbox.id().toString(), "0-9"))
                .thenReturn(Flux.just(new AiEventStreams.StoredEvent(
                        "1-0",
                        payload(
                                sandbox.id(),
                                1,
                                SandboxEvents.OPERATION_FINISHED,
                                Map.of("status", "succeeded")))));
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(sandbox, "0-9"))
                .assertNext(event -> assertThat(event.eventId()).isEqualTo("1-0"))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(SandboxEvents.STOPPED);
                    assertThat(event.data()).containsEntry("fallback", true);
                })
                .verifyComplete();
    }

    @Test
    void rejectsMalformedLastEventIdsBeforeSubscribingToRedis() {
        var sandbox = sandbox(Sandbox.Status.READY);

        StepVerifier.create(events.stream(sandbox, "not-a-stream-id"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(400);
                            assertThat(api.getCode()).isEqualTo("sandbox-event-id-invalid");
                        }))
                .verify();
    }

    @Test
    void emitsKeepalivePingsWithoutAStreamId() {
        var sandbox = sandbox(Sandbox.Status.REQUESTED);
        var keepalives = new SandboxEvents(
                streams, Duration.ofMillis(10), Clock.fixed(NOW, ZoneOffset.UTC));
        when(streams.replay(sandbox.id().toString(), null)).thenReturn(Flux.empty());
        when(streams.live()).thenReturn(Flux.never());

        StepVerifier.create(keepalives.stream(sandbox, null).take(2).collectList())
                .assertNext(received -> {
                    assertThat(received).hasSize(2);
                    assertThat(received.get(0).type()).isEqualTo(SandboxEvents.CREATED);
                    assertThat(received.get(0).data()).containsEntry("fallback", true);
                    assertThat(received.get(1).type()).isEqualTo(SandboxEvents.PING);
                    assertThat(received.get(1).eventId()).isNull();
                })
                .verifyComplete();
    }

    private static String payload(
            UUID sandboxId, long seq, String type, Map<String, Object> data) {
        var eventData = new LinkedHashMap<String, Object>();
        eventData.put("sandboxId", sandboxId);
        eventData.putAll(data);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("seq", seq);
        payload.put("sandboxId", sandboxId);
        payload.put("type", type);
        payload.put("data", eventData);
        payload.put("createdAt", NOW.toString());
        return JSON.writeValueAsString(payload);
    }

    private static Sandbox sandbox(Sandbox.Status status) {
        return new Sandbox(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                UUID.randomUUID(),
                null,
                "agentrun",
                "default",
                "cn-shanghai",
                status,
                "/home/pudans/workspace",
                NOW.plusSeconds(3600),
                NOW,
                NOW,
                status == Sandbox.Status.REQUESTED ? null : NOW,
                status.terminal() ? NOW : null,
                status == Sandbox.Status.FAILED ? "sandbox-provider-failed" : null,
                status == Sandbox.Status.FAILED ? "provider failed" : null);
    }
}
