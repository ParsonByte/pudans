package cn.tolim.pudans.core.creation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskAttemptView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskEvent;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskPage;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskResultView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStartRequest;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStatusView;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskSummary;
import cn.tolim.pudans.core.creation.task.GenerationTaskAudit;
import cn.tolim.pudans.core.creation.task.GenerationTaskEvents;
import cn.tolim.pudans.core.creation.task.GenerationTasks;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GenerationTaskEndpointTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final UUID TASK_ID = UUID.randomUUID();

    private static final UUID MODEL_ID = UUID.randomUUID();

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    GenerationTasks tasks;

    @Mock
    GenerationTaskEvents events;

    @Mock
    ReactiveRequestIdentity identity;

    @Mock
    GenerationTaskAudit audits;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new GenerationTaskEndpoint(tasks, events, identity, audits);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void startsANewTaskAndRecordsTheIdempotentRequest() {
        var summary = summary(GenerationTask.Status.QUEUED);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.start(eq(ALICE), any(GenerationTaskStartRequest.class), eq("request-1")))
                .thenReturn(Mono.just(new GenerationTasks.StartResult(summary, true)));
        when(audits.record(any(), eq("START_GENERATION_TASK"), eq(summary), eq(true), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/generationtasks/-/start")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(startRequest())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(TASK_ID.toString())
                .jsonPath("$.status")
                .isEqualTo("queued");

        var extra = ArgumentCaptor.forClass(Map.class);
        verify(audits).record(
                any(),
                eq("START_GENERATION_TASK"),
                eq(summary),
                eq(true),
                extra.capture());
        assertThat(extra.getValue()).containsEntry("replayed", false);
    }

    @Test
    void returnsTheExistingTaskWhenIdempotencyReplays() {
        var summary = summary(GenerationTask.Status.QUEUED);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.start(eq(ALICE), any(GenerationTaskStartRequest.class), eq("request-1")))
                .thenReturn(Mono.just(new GenerationTasks.StartResult(summary, false)));
        when(audits.record(any(), eq("START_GENERATION_TASK"), eq(summary), eq(true), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/generationtasks/-/start")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(startRequest())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(TASK_ID.toString());

        var extra = ArgumentCaptor.forClass(Map.class);
        verify(audits).record(
                any(),
                eq("START_GENERATION_TASK"),
                eq(summary),
                eq(true),
                extra.capture());
        assertThat(extra.getValue()).containsEntry("replayed", true);
    }

    @Test
    void rejectsAnEmptyStartBodyBeforeResolvingTheIdentity() {
        client.post()
                .uri("/generationtasks/-/start")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, tasks, audits);
    }

    @Test
    void listsMineWithTheRequestedCursorAndLimit() {
        var page = new GenerationTaskPage(List.of(summary(GenerationTask.Status.QUEUED)), "next");
        var before = NOW.minusSeconds(30);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.mine(ALICE, before, 5)).thenReturn(Mono.just(page));

        client.get()
                .uri(uri -> uri.path("/generationtasks/-/mine")
                        .queryParam("before", before.toString())
                        .queryParam("limit", 5)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].id")
                .isEqualTo(TASK_ID.toString())
                .jsonPath("$.nextCursor")
                .isEqualTo("next");

        verify(tasks).mine(ALICE, before, 5);
    }

    @Test
    void rejectsMalformedListParameters() {
        client.get()
                .uri("/generationtasks/-/mine?before=yesterday")
                .exchange()
                .expectStatus()
                .isBadRequest();
        client.get()
                .uri("/generationtasks/-/mine?limit=all")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, tasks);
    }

    @Test
    void treatsMalformedTaskNamesAsMissing() {
        client.get()
                .uri("/generationtasks/not-a-uuid/status")
                .exchange()
                .expectStatus()
                .isNotFound();

        verifyNoInteractions(identity, tasks);
    }

    @Test
    void returnsStatusWithAttempts() {
        var task = task(GenerationTask.Status.PROVIDER_RUNNING);
        var attempt = new GenerationTaskAttemptView(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                MODEL_ID,
                "provider-task-1",
                "provider-request-1",
                GenerationTask.AttemptStatus.RUNNING,
                null,
                null,
                NOW,
                null);
        var view = new GenerationTaskStatusView(
                summary(task.status()),
                null,
                null,
                null,
                null,
                task.settings(),
                task.inputAssetIds(),
                List.of(attempt));
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.status(ALICE, TASK_ID)).thenReturn(Mono.just(view));

        client.get()
                .uri("/generationtasks/{name}/status", TASK_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.task.id")
                .isEqualTo(TASK_ID.toString())
                .jsonPath("$.attempts[0].providerTaskId")
                .isEqualTo("provider-task-1");
    }

    @Test
    void returnsTheArchivedResultView() {
        var result = new GenerationTaskResultView(
                TASK_ID,
                "succeeded",
                List.of(),
                List.of(new GenerationTaskResultView.ArchivedResult(
                        "file-1", "result.png", "image/png", 3L, "image")),
                "pending-p4",
                "结果已归档为 StorageFile，逻辑资产登记由 P4 完成。",
                NOW);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.result(ALICE, TASK_ID)).thenReturn(Mono.just(result));

        client.get()
                .uri("/generationtasks/{name}/result", TASK_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.registrationState")
                .isEqualTo("pending-p4")
                .jsonPath("$.storageFiles[0].fileId")
                .isEqualTo("file-1");
    }

    @Test
    void cancelsAndAuditsTheTask() {
        var summary = summary(GenerationTask.Status.CANCELLED);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.cancel(ALICE, TASK_ID)).thenReturn(Mono.just(summary));
        when(audits.record(any(), eq("CANCEL_GENERATION_TASK"), eq(summary), eq(true), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/generationtasks/{name}/cancel", TASK_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("cancelled");

        verify(audits).record(
                any(), eq("CANCEL_GENERATION_TASK"), eq(summary), eq(true), anyMap());
    }

    @Test
    void retriesAndAuditsTheProviderState() {
        var summary = summary(GenerationTask.Status.RETRY_QUEUED);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.retry(ALICE, TASK_ID)).thenReturn(Mono.just(summary));
        when(audits.record(any(), eq("RETRY_GENERATION_TASK"), eq(summary), eq(true), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/generationtasks/{name}/-/retry", TASK_ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("retry_queued");

        var extra = ArgumentCaptor.forClass(Map.class);
        verify(audits).record(
                any(),
                eq("RETRY_GENERATION_TASK"),
                eq(summary),
                eq(true),
                extra.capture());
        assertThat(extra.getValue()).containsEntry("providerRequestSent", true);
    }

    @Test
    void streamsReplayedEventsWithTheRedisId() {
        var task = task(GenerationTask.Status.SUCCEEDED);
        var event = new GenerationTaskEvent(
                "1700000000000-0",
                3,
                null,
                null,
                GenerationTaskEvents.TASK_RESULT,
                Map.of("taskId", TASK_ID, "status", "succeeded"),
                NOW);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.requireOwned(ALICE, TASK_ID)).thenReturn(Mono.just(task));
        when(events.stream(task, "1600000000000-0")).thenReturn(Flux.just(event));

        client.get()
                .uri("/generationtasks/{name}/events", TASK_ID)
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
                .value(body -> assertThat(body)
                        .contains("id:1700000000000-0")
                        .contains("event:task.result")
                        .contains("\"type\":\"task.result\""));
    }

    @Test
    void rejectsAMalformedLastEventIdBeforeResolvingTheIdentity() {
        client.get()
                .uri("/generationtasks/{name}/events", TASK_ID)
                .header("Last-Event-ID", "not-a-stream-id")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(identity, tasks, events);
    }

    @Test
    void omitsSseIdsFromFallbackEvents() {
        var task = task(GenerationTask.Status.SUCCEEDED);
        var event = new GenerationTaskEvent(
                "fallback-" + TASK_ID + "-succeeded-1",
                0,
                null,
                null,
                GenerationTaskEvents.TASK_RESULT,
                Map.of("taskId", TASK_ID, "fallback", true),
                NOW);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.requireOwned(ALICE, TASK_ID)).thenReturn(Mono.just(task));
        when(events.stream(task, null)).thenReturn(Flux.just(event));

        client.get()
                .uri("/generationtasks/{name}/events", TASK_ID)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("event:task.result")
                        .doesNotContain("id:fallback-"));
    }

    @Test
    void hidesEventsForTasksOutsideTheCurrentOwner() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(tasks.requireOwned(ALICE, TASK_ID)).thenReturn(Mono.error(new ApiException(
                HttpStatus.NOT_FOUND, "generation-task-not-found", "生成任务不存在。")));

        client.get()
                .uri("/generationtasks/{name}/events", TASK_ID)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isNotFound();

        verify(events, never()).stream(any(), any());
    }

    private static GenerationTaskStartRequest startRequest() {
        return new GenerationTaskStartRequest(
                "image",
                "text",
                MODEL_ID,
                "prompt",
                Map.of("ratio", "1:1"),
                List.of(),
                null,
                null,
                null,
                null,
                null);
    }

    private static GenerationTask task(GenerationTask.Status status) {
        return new GenerationTask(
                TASK_ID,
                ALICE.ownerId(),
                ALICE.workspaceId(),
                null,
                null,
                null,
                null,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                MODEL_ID,
                "prompt",
                Map.of("ratio", "1:1"),
                List.of(),
                status,
                status != GenerationTask.Status.QUEUED,
                status == GenerationTask.Status.PROVIDER_RUNNING ? "provider-task-1" : null,
                List.of(),
                status == GenerationTask.Status.FAILED ? "provider-failed" : null,
                status == GenerationTask.Status.FAILED ? "provider failed" : null,
                null,
                "idempotency",
                NOW,
                status == GenerationTask.Status.QUEUED ? null : NOW,
                status.terminal() ? NOW : null);
    }

    private static GenerationTaskSummary summary(GenerationTask.Status status) {
        var task = task(status);
        return new GenerationTaskSummary(
                task.id(),
                task.mediaType().getCode(),
                task.operation().getCode(),
                task.modelId(),
                task.prompt(),
                task.status(),
                task.providerRequestSent(),
                task.resultAssetIds(),
                task.errorCode(),
                task.errorMessage(),
                task.createdAt(),
                task.startedAt(),
                task.finishedAt());
    }
}
