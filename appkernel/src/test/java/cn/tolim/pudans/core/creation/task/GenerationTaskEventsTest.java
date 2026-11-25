package cn.tolim.pudans.core.creation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.GenerationTask;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GenerationTaskEventsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    AiEventStreams streams;

    private GenerationTaskEvents events;

    @BeforeEach
    void setUp() {
        events = new GenerationTaskEvents(
                streams, Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesAnOrderedTaskEventAndUsesTheRedisId() {
        var task = task(GenerationTask.Status.PROVIDER_RUNNING);
        when(streams.nextSequence(GenerationTaskEvents.streamId(task))).thenReturn(Mono.just(7L));
        when(streams.append(eq(GenerationTaskEvents.streamId(task)), anyString()))
                .thenReturn(Mono.just("1-7"));

        StepVerifier.create(events.publish(
                        task,
                        GenerationTaskEvents.TASK_PROGRESS,
                        Map.of("taskId", task.id())))
                .assertNext(event -> {
                    assertThat(event.eventId()).isEqualTo("1-7");
                    assertThat(event.seq()).isEqualTo(7);
                    assertThat(event.type()).isEqualTo(GenerationTaskEvents.TASK_PROGRESS);
                    assertThat(event.createdAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void replaysStoredEventsWithTheirRedisIds() {
        var task = task(GenerationTask.Status.PROVIDER_RUNNING);
        when(streams.replay(GenerationTaskEvents.streamId(task), "0-9"))
                .thenReturn(Flux.just(new AiEventStreams.StoredEvent(
                        "1-0", payload(task, 1, GenerationTaskEvents.TASK_PROGRESS))));
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(task, "0-9").take(1))
                .assertNext(event -> {
                    assertThat(event.eventId()).isEqualTo("1-0");
                    assertThat(event.seq()).isEqualTo(1);
                    assertThat(event.type()).isEqualTo(GenerationTaskEvents.TASK_PROGRESS);
                })
                .verifyComplete();
    }

    @Test
    void emitsOnlyTheRealFinalStateWhenReplayHasExpired() {
        var task = task(GenerationTask.Status.SUCCEEDED);
        when(streams.replay(GenerationTaskEvents.streamId(task), null)).thenReturn(Flux.empty());
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(task, null))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(GenerationTaskEvents.TASK_RESULT);
                    assertThat(event.data())
                            .containsEntry("fallback", true)
                            .containsEntry("status", "succeeded");
                    assertThat(event.eventId()).startsWith("fallback-");
                })
                .verifyComplete();
    }

    @Test
    void appendsTheFinalStateWhenReplayDoesNotContainATerminalEvent() {
        var task = task(GenerationTask.Status.FAILED);
        when(streams.replay(GenerationTaskEvents.streamId(task), "0-9"))
                .thenReturn(Flux.just(new AiEventStreams.StoredEvent(
                        "1-0", payload(task, 1, GenerationTaskEvents.TASK_PROGRESS))));
        when(streams.live()).thenReturn(Flux.empty());

        StepVerifier.create(events.stream(task, "0-9"))
                .assertNext(event -> assertThat(event.eventId()).isEqualTo("1-0"))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(GenerationTaskEvents.TASK_FAILED);
                    assertThat(event.data()).containsEntry("fallback", true);
                })
                .verifyComplete();
    }

    @Test
    void rejectsMalformedLastEventIdsBeforeSubscribingToRedis() {
        var task = task(GenerationTask.Status.QUEUED);

        StepVerifier.create(events.stream(task, "not-a-stream-id"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(400);
                            assertThat(api.getCode()).isEqualTo("generation-event-id-invalid");
                        }))
                .verify();
    }

    @Test
    void emitsKeepalivePingsWithoutAStreamId() {
        var task = task(GenerationTask.Status.QUEUED);
        var keepalives = new GenerationTaskEvents(
                streams, Duration.ofMillis(10), Clock.fixed(NOW, ZoneOffset.UTC));
        when(streams.replay(GenerationTaskEvents.streamId(task), null)).thenReturn(Flux.empty());
        when(streams.live()).thenReturn(Flux.never());

        StepVerifier.create(keepalives.stream(task, null).take(2).collectList())
                .assertNext(received -> {
                    assertThat(received).hasSize(2);
                    assertThat(received.get(0).type()).isEqualTo(GenerationTaskEvents.TASK_PROGRESS);
                    assertThat(received.get(0).eventId()).startsWith("fallback-");
                    assertThat(received.get(1).type()).isEqualTo(GenerationTaskEvents.PING);
                    assertThat(received.get(1).eventId()).isNull();
                    assertThat(received.get(1).seq()).isZero();
                })
                .verifyComplete();
    }

    private static String payload(GenerationTask task, long seq, String type) {
        var data = new LinkedHashMap<String, Object>();
        data.put("taskId", task.id());
        var payload = new LinkedHashMap<String, Object>();
        payload.put("seq", seq);
        payload.put("runId", task.runId());
        payload.put("sessionId", task.sessionId());
        payload.put("type", type);
        payload.put("data", data);
        payload.put("createdAt", NOW.toString());
        return JSON.writeValueAsString(payload);
    }

    private static GenerationTask task(GenerationTask.Status status) {
        var finishedAt = status.terminal() ? NOW : null;
        return new GenerationTask(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                UUID.randomUUID(),
                "prompt",
                Map.of(),
                List.of(),
                status,
                status != GenerationTask.Status.QUEUED,
                null,
                List.of(),
                status == GenerationTask.Status.FAILED ? "provider-failed" : null,
                status == GenerationTask.Status.FAILED ? "provider failed" : null,
                null,
                "idempotency",
                NOW,
                status == GenerationTask.Status.QUEUED ? null : NOW,
                finishedAt);
    }
}
