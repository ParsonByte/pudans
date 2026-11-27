package cn.tolim.pudans.core.creation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStartRequest;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskSummary;
import cn.tolim.pudans.core.creation.provider.GenerationModelCatalog;
import cn.tolim.pudans.core.creation.provider.GenerationModelProfile;
import cn.tolim.pudans.core.creation.provider.MediaGenerationGateway;
import cn.tolim.pudans.core.creation.provider.MediaGenerationGateways;
import cn.tolim.pudans.core.storage.FileRecords;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.GenerationTaskStore;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GenerationTasksTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    private static final UUID MODEL_ID = UUID.randomUUID();

    @Mock
    GenerationTaskStore store;

    @Mock
    AiTaskQueue queue;

    @Mock
    ObjectProvider<GenerationModelCatalog> catalogs;

    @Mock
    GenerationModelCatalog catalog;

    @Mock
    MediaGenerationGateways gateways;

    @Mock
    MediaGenerationGateway gateway;

    @Mock
    GenerationTaskEvents events;

    @Mock
    GenerationTaskCancellations cancellations;

    @Mock
    FileRecords fileRecords;

    private GenerationTasks tasks;

    @BeforeEach
    void setUp() {
        tasks = new GenerationTasks(
                store, queue, catalogs, gateways, events, cancellations, fileRecords);
        tasks.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsTheExistingTaskForARepeatedIdempotencyKeyWithoutLoadingTheModel() {
        var existing = task(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                GenerationTask.Status.QUEUED,
                false,
                null);
        when(store.findByIdempotencyKey(anyString())).thenReturn(Mono.just(existing));

        StepVerifier.create(tasks.start(ALICE, request(), "request-1"))
                .assertNext(result -> {
                    assertThat(result.created()).isFalse();
                    assertThat(result.task().id()).isEqualTo(existing.id());
                })
                .verifyComplete();

        verify(catalogs, never()).getIfAvailable();
        verify(queue, never()).enqueue(any(), anyString());
    }

    @Test
    void rejectsAnIdempotencyReplayFromAnotherOwner() {
        var existing = task(
                UUID.randomUUID(),
                "bob",
                "workspace-a",
                GenerationTask.Status.QUEUED,
                false,
                null);
        when(store.findByIdempotencyKey(anyString())).thenReturn(Mono.just(existing));

        StepVerifier.create(tasks.start(ALICE, request(), "request-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(409);
                            assertThat(api.getCode()).isEqualTo("generation-idempotency-key-conflict");
                        }))
                .verify();
    }

    @Test
    void failsExplicitlyWhenNoModelCatalogIsConfigured() {
        when(store.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(catalogs.getIfAvailable()).thenReturn(null);

        StepVerifier.create(tasks.start(ALICE, request(), "request-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(503);
                            assertThat(api.getCode()).isEqualTo("generation-model-catalog-not-configured");
                        }))
                .verify();
    }

    @Test
    void failsExplicitlyWhenTheResolvedProviderHasNoGateway() {
        when(store.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        when(catalog.resolve(MODEL_ID)).thenReturn(Mono.just(profile()));
        when(gateways.find("provider-a")).thenReturn(Optional.empty());

        StepVerifier.create(tasks.start(ALICE, request(), "request-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(503);
                            assertThat(api.getCode()).isEqualTo("generation-provider-not-configured");
                        }))
                .verify();

        verify(store, never()).insertIfAbsent(any());
    }

    @Test
    void createsAndQueuesAValidatedTask() {
        var created = task(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                GenerationTask.Status.QUEUED,
                false,
                null);
        when(store.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        when(catalog.resolve(MODEL_ID)).thenReturn(Mono.just(profile()));
        when(catalog.validate(
                        any(GenerationModelProfile.class),
                        eq(GenerationTask.MediaType.IMAGE),
                        eq(GenerationTask.Operation.TEXT),
                        eq("prompt"),
                        any(),
                        anyList()))
                .thenReturn(Mono.empty());
        when(gateways.find("provider-a")).thenReturn(Optional.of(gateway));
        when(store.insertIfAbsent(any())).thenReturn(Mono.just(
                new GenerationTaskStore.Insertion(created, true)));
        when(events.publish(eq(created), eq(GenerationTaskEvents.TASK_QUEUED), any()))
                .thenReturn(Mono.empty());
        when(queue.enqueue(created.id(), "created")).thenReturn(Mono.just("1-0"));

        StepVerifier.create(tasks.start(ALICE, request(), "request-1"))
                .assertNext(result -> {
                    assertThat(result.created()).isTrue();
                    assertThat(result.task().id()).isEqualTo(created.id());
                })
                .verifyComplete();

        var inserted = ArgumentCaptor.forClass(GenerationTask.class);
        verify(store).insertIfAbsent(inserted.capture());
        assertThat(inserted.getValue().ownerId()).isEqualTo("alice");
        assertThat(inserted.getValue().workspaceId()).isEqualTo("workspace-a");
        assertThat(inserted.getValue().idempotencyKey()).isNotEqualTo("request-1");
        verify(queue).enqueue(created.id(), "created");
    }

    @Test
    void cancelsALocalTaskWithoutCallingTheProvider() {
        var queued = task(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                GenerationTask.Status.QUEUED,
                false,
                null);
        var cancelled = withStatus(queued, GenerationTask.Status.CANCELLED);
        when(store.findById(queued.id())).thenReturn(Mono.just(queued));
        when(cancellations.markTask(queued.id())).thenReturn(Mono.empty());
        when(store.cancel(eq(queued.id()), any(), isNull(), isNull(), any(Instant.class)))
                .thenReturn(Mono.just(cancelled));
        when(events.publish(eq(cancelled), eq(GenerationTaskEvents.TASK_CANCELLED), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(tasks.cancel(ALICE, queued.id()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(GenerationTask.Status.CANCELLED))
                .verifyComplete();

        verify(cancellations).markTask(queued.id());
        verify(cancellations, never()).markRun(any());
        verifyNoInteractions(gateways);
    }

    @Test
    void listsEveryTaskForAdministrativeInspection() {
        var queued = task(
                UUID.randomUUID(),
                "bob",
                "workspace-b",
                GenerationTask.Status.QUEUED,
                false,
                null);
        when(store.listAll(isNull(), eq(20))).thenReturn(Flux.just(queued));

        var result = tasks.listAll(null, 20).block();

        assertThat(result).isNotNull();
        assertThat(result.items()).extracting(GenerationTaskSummary::id)
                .containsExactly(queued.id());
    }

    @Test
    void forceCancelsAnotherOwnersLocalTask() {
        var queued = task(
                UUID.randomUUID(),
                "bob",
                "workspace-b",
                GenerationTask.Status.QUEUED,
                false,
                null);
        var cancelled = withStatus(queued, GenerationTask.Status.CANCELLED);
        when(store.findById(queued.id())).thenReturn(Mono.just(queued));
        when(cancellations.markTask(queued.id())).thenReturn(Mono.empty());
        when(store.cancel(eq(queued.id()), any(), isNull(), isNull(), any(Instant.class)))
                .thenReturn(Mono.just(cancelled));
        when(events.publish(eq(cancelled), eq(GenerationTaskEvents.TASK_CANCELLED), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(tasks.forceCancel(queued.id()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(GenerationTask.Status.CANCELLED))
                .verifyComplete();

        verify(cancellations).markTask(queued.id());
        verify(cancellations, never()).markRun(any());
        verifyNoInteractions(gateways);
    }

    @Test
    void asksTheProviderToCancelAfterAcceptance() {
        var attempt = attempt(UUID.randomUUID(), GenerationTask.AttemptStatus.RUNNING);
        var running = task(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                GenerationTask.Status.PROVIDER_RUNNING,
                true,
                "provider-task-1");
        var withAttempt = new GenerationTask(
                running.id(),
                running.ownerId(),
                running.workspaceId(),
                running.sessionId(),
                running.runId(),
                running.parentTaskId(),
                running.parentAssetId(),
                running.mediaType(),
                running.operation(),
                running.modelId(),
                running.prompt(),
                running.settings(),
                running.inputAssetIds(),
                running.status(),
                running.providerRequestSent(),
                running.providerTaskId(),
                running.resultAssetIds(),
                running.errorCode(),
                running.errorMessage(),
                running.quotaReservationId(),
                running.idempotencyKey(),
                running.createdAt(),
                running.startedAt(),
                running.finishedAt());
        var cancelled = withStatus(withAttempt, GenerationTask.Status.CANCELLED);
        when(store.findById(withAttempt.id())).thenReturn(Mono.just(withAttempt));
        when(cancellations.markTask(withAttempt.id())).thenReturn(Mono.empty());
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        when(catalog.resolve(MODEL_ID)).thenReturn(Mono.just(profile()));
        when(gateways.find("provider-a")).thenReturn(Optional.of(gateway));
        when(store.listAttempts(withAttempt.id())).thenReturn(Flux.just(attempt));
        when(gateway.cancel(any())).thenReturn(Mono.empty());
        when(store.cancel(eq(withAttempt.id()), any(), isNull(), isNull(), any(Instant.class)))
                .thenReturn(Mono.just(cancelled));
        when(events.publish(eq(cancelled), eq(GenerationTaskEvents.TASK_CANCELLED), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(tasks.cancel(ALICE, withAttempt.id()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(GenerationTask.Status.CANCELLED))
                .verifyComplete();

        var reference = ArgumentCaptor.forClass(MediaGenerationGateway.Reference.class);
        verify(gateway).cancel(reference.capture());
        assertThat(reference.getValue().clientReference()).isEqualTo(attempt.id().toString());
        assertThat(reference.getValue().providerTaskId()).isEqualTo("provider-task-1");
        assertThat(reference.getValue().channelId()).isEqualTo(attempt.channelId());
        assertThat(reference.getValue().providerModel()).isEqualTo("provider-model-a");
        assertThat(reference.getValue().mediaType()).isEqualTo(GenerationTask.MediaType.IMAGE);
    }

    @Test
    void refusesToRetryWhileTheProviderTaskIsStillActive() {
        var attempt = attempt(UUID.randomUUID(), GenerationTask.AttemptStatus.RUNNING);
        var failed = task(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                GenerationTask.Status.FAILED,
                true,
                "provider-task-1");
        when(store.findById(failed.id())).thenReturn(Mono.just(failed));
        when(store.countAttempts(failed.id())).thenReturn(Mono.just(1L));
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        when(catalog.resolve(MODEL_ID)).thenReturn(Mono.just(profile()));
        when(gateways.find("provider-a")).thenReturn(Optional.of(gateway));
        when(store.listAttempts(failed.id())).thenReturn(Flux.just(attempt));
        when(gateway.find(any())).thenReturn(Mono.just(new MediaGenerationGateway.State(
                MediaGenerationGateway.Phase.RUNNING,
                "provider-task-1",
                null,
                null,
                null,
                null)));

        StepVerifier.create(tasks.retry(ALICE, failed.id()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(409);
                            assertThat(api.getCode()).isEqualTo("generation-provider-task-active");
                        }))
                .verify();

        verify(store, never()).retry(any(), anyBoolean(), any());
        verify(queue, never()).enqueue(any(), anyString());
    }

    @Test
    void resumesArchivingWhenProviderReportsSuccessOnRetry() {
        var attempt = attempt(UUID.randomUUID(), GenerationTask.AttemptStatus.RUNNING);
        var failed = task(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                GenerationTask.Status.FAILED,
                true,
                "provider-task-1");
        var retryQueued = withStatus(failed, GenerationTask.Status.RETRY_QUEUED);
        when(store.findById(failed.id())).thenReturn(Mono.just(failed));
        when(store.countAttempts(failed.id())).thenReturn(Mono.just(1L));
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        when(catalog.resolve(MODEL_ID)).thenReturn(Mono.just(profile()));
        when(gateways.find("provider-a")).thenReturn(Optional.of(gateway));
        when(store.listAttempts(failed.id())).thenReturn(Flux.just(attempt));
        when(gateway.find(any())).thenReturn(Mono.just(new MediaGenerationGateway.State(
                MediaGenerationGateway.Phase.SUCCEEDED,
                "provider-task-1",
                null,
                null,
                null,
                new MediaGenerationGateway.Result(List.of()))));
        when(store.retry(failed.id(), true, "provider-task-1"))
                .thenReturn(Mono.just(retryQueued));
        when(events.publish(eq(retryQueued), eq(GenerationTaskEvents.TASK_QUEUED), any()))
                .thenReturn(Mono.empty());
        when(queue.enqueue(retryQueued.id(), "retry")).thenReturn(Mono.just("2-0"));

        StepVerifier.create(tasks.retry(ALICE, failed.id()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(GenerationTask.Status.RETRY_QUEUED))
                .verifyComplete();

        verify(store).retry(failed.id(), true, "provider-task-1");
        verify(queue).enqueue(retryQueued.id(), "retry");
    }

    private static GenerationTaskStartRequest request() {
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

    private static GenerationModelProfile profile() {
        return new GenerationModelProfile(
                MODEL_ID,
                "provider-a",
                "provider-model-a",
                UUID.randomUUID(),
                Set.of(GenerationTask.MediaType.IMAGE),
                Set.of(GenerationTask.Operation.TEXT));
    }

    private static GenerationTask task(
            UUID id,
            String owner,
            String workspace,
            GenerationTask.Status status,
            boolean providerRequestSent,
            String providerTaskId) {
        return new GenerationTask(
                id,
                owner,
                workspace,
                null,
                null,
                null,
                null,
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                MODEL_ID,
                "prompt",
                Map.of(),
                List.of(),
                status,
                providerRequestSent,
                providerTaskId,
                List.of(),
                null,
                null,
                null,
                "idempotency",
                NOW,
                NOW,
                status.terminal() ? NOW : null);
    }

    private static GenerationTask.Attempt attempt(
            UUID id, GenerationTask.AttemptStatus status) {
        return new GenerationTask.Attempt(
                id,
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                MODEL_ID,
                "provider-task-1",
                "provider-request-1",
                status,
                null,
                null,
                NOW,
                null);
    }

    private static GenerationTask withStatus(
            GenerationTask task, GenerationTask.Status status) {
        return new GenerationTask(
                task.id(),
                task.ownerId(),
                task.workspaceId(),
                task.sessionId(),
                task.runId(),
                task.parentTaskId(),
                task.parentAssetId(),
                task.mediaType(),
                task.operation(),
                task.modelId(),
                task.prompt(),
                task.settings(),
                task.inputAssetIds(),
                status,
                task.providerRequestSent(),
                task.providerTaskId(),
                task.resultAssetIds(),
                task.errorCode(),
                task.errorMessage(),
                task.quotaReservationId(),
                task.idempotencyKey(),
                task.createdAt(),
                task.startedAt(),
                status.terminal() ? NOW : null);
    }
}
