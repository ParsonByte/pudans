package cn.tolim.pudans.core.creation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.core.creation.provider.GenerationModelProfile;
import cn.tolim.pudans.core.creation.provider.MediaGenerationGateway;
import cn.tolim.pudans.core.storage.StorageRoutes;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.r2dbc.creation.GenerationTaskStore;
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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GenerationTaskPipelineTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final UUID MODEL_ID = UUID.randomUUID();

    @Mock
    GenerationTasks tasks;

    @Mock
    StorageRoutes storageRoutes;

    @Mock
    StorageTransfer storageTransfer;

    @Mock
    ObjectProvider<GenerationResultAssets> resultAssets;

    @Mock
    GenerationTaskEvents events;

    @Mock
    GenerationTaskAudit audit;

    @Mock
    MediaGenerationGateway gateway;

    private GenerationTaskPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new GenerationTaskPipeline(
                tasks, storageRoutes, storageTransfer, resultAssets, events, audit);
        pipeline.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submitsThenArchivesTheRealProviderResult() {
        var queued = task(GenerationTask.Status.QUEUED, false, null);
        var dispatching = task(queued.id(), GenerationTask.Status.DISPATCHING, false, null);
        var running = task(queued.id(), GenerationTask.Status.PROVIDER_RUNNING, true, "provider-task-1");
        var downloading = task(queued.id(), GenerationTask.Status.DOWNLOADING, true, "provider-task-1");
        var archiving = task(queued.id(), GenerationTask.Status.ARCHIVING, true, "provider-task-1");
        var succeeded = task(queued.id(), GenerationTask.Status.SUCCEEDED, true, "provider-task-1");
        var attempt = attempt(queued.id(), GenerationTask.AttemptStatus.REQUESTING);
        var profile = profile();
        var route = new GenerationTasks.Route(profile, gateway);
        var asset = providerAsset("provider-asset-1");
        var policy = policy();
        var file = storageFile();

        when(tasks.find(queued.id()))
                .thenReturn(Mono.just(queued), Mono.just(archiving));
        when(tasks.latestAttempt(queued.id())).thenReturn(Mono.just(Optional.empty()));
        when(tasks.nextAttemptNumber(queued.id())).thenReturn(Mono.just(1));
        when(tasks.beginProcessing(eq(queued), any()))
                .thenReturn(Mono.just(new GenerationTaskStore.Processing(dispatching, attempt)));
        when(tasks.cancelled(any())).thenReturn(Mono.just(false));
        when(tasks.routeFor(MODEL_ID, "workspace-a")).thenReturn(Mono.just(route));
        when(tasks.markProviderRequestStarted(queued.id())).thenReturn(Mono.just(dispatching));
        when(gateway.submit(any())).thenReturn(Mono.just(submission(asset)));
        when(tasks.markAttemptSubmitted(eq(attempt.id()), anyString(), anyString(), any()))
                .thenReturn(Mono.just(attempt));
        when(tasks.confirmProviderRunning(eq(queued.id()), eq("provider-task-1")))
                .thenReturn(Mono.just(running));
        when(tasks.archivedFiles(any())).thenReturn(Mono.just(List.of()));
        when(storageRoutes.resolve(StorageRoute.Purpose.CREATION_RESULT))
                .thenReturn(Mono.just(policy));
        when(tasks.transition(
                        queued.id(),
                        Set.of(GenerationTask.Status.PROVIDER_RUNNING),
                        GenerationTask.Status.DOWNLOADING))
                .thenReturn(Mono.just(downloading));
        when(tasks.transition(
                        queued.id(),
                        Set.of(GenerationTask.Status.DOWNLOADING),
                        GenerationTask.Status.ARCHIVING))
                .thenReturn(Mono.just(archiving));
        when(storageTransfer.upload(any())).thenReturn(Mono.just(file));
        when(resultAssets.getIfAvailable()).thenReturn(null);
        when(tasks.succeed(queued.id(), attempt.id(), List.of())).thenReturn(Mono.just(succeeded));
        when(events.publish(any(), anyString(), anyMap())).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(queued.id()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(GenerationTask.Status.SUCCEEDED);
                    assertThat(result.id()).isEqualTo(queued.id());
                })
                .verifyComplete();

        var request = ArgumentCaptor.forClass(StorageWriteRequest.class);
        verify(storageTransfer).upload(request.capture());
        assertThat(request.getValue().module()).isEqualTo("creation");
        assertThat(request.getValue().attributes())
                .containsEntry("generationTaskId", queued.id().toString())
                .containsEntry("providerAssetId", "provider-asset-1")
                .containsEntry("mediaType", "image");
        verify(gateway).submit(any());
    }

    @Test
    void resumesAcceptedProviderWorkWithoutSubmittingAgain() {
        var running = task(GenerationTask.Status.PROVIDER_RUNNING, true, "provider-task-1");
        var downloading = task(running.id(), GenerationTask.Status.DOWNLOADING, true, "provider-task-1");
        var archiving = task(running.id(), GenerationTask.Status.ARCHIVING, true, "provider-task-1");
        var succeeded = task(running.id(), GenerationTask.Status.SUCCEEDED, true, "provider-task-1");
        var attempt = attempt(running.id(), GenerationTask.AttemptStatus.RUNNING);
        var profile = profile();
        var route = new GenerationTasks.Route(profile, gateway);
        var asset = providerAsset("provider-asset-1");

        when(tasks.find(running.id()))
                .thenReturn(Mono.just(running), Mono.just(archiving));
        when(tasks.latestAttempt(running.id()))
                .thenReturn(Mono.just(Optional.of(attempt)));
        when(tasks.cancelled(any())).thenReturn(Mono.just(false));
        when(tasks.routeFor(MODEL_ID, "workspace-a")).thenReturn(Mono.just(route));
        when(gateway.find(any())).thenReturn(Mono.just(providerState(asset)));
        when(tasks.archivedFiles(any())).thenReturn(Mono.just(List.of()));
        when(storageRoutes.resolve(StorageRoute.Purpose.CREATION_RESULT))
                .thenReturn(Mono.just(policy()));
        when(tasks.transition(
                        running.id(),
                        Set.of(GenerationTask.Status.PROVIDER_RUNNING),
                        GenerationTask.Status.DOWNLOADING))
                .thenReturn(Mono.just(downloading));
        when(tasks.transition(
                        running.id(),
                        Set.of(GenerationTask.Status.DOWNLOADING),
                        GenerationTask.Status.ARCHIVING))
                .thenReturn(Mono.just(archiving));
        when(storageTransfer.upload(any())).thenReturn(Mono.just(storageFile()));
        when(resultAssets.getIfAvailable()).thenReturn(null);
        when(tasks.succeed(running.id(), attempt.id(), List.of())).thenReturn(Mono.just(succeeded));
        when(events.publish(any(), anyString(), anyMap())).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(running.id()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(GenerationTask.Status.SUCCEEDED))
                .verifyComplete();

        verify(gateway, never()).submit(any());
        verify(gateway).find(any());
    }

    @Test
    void cancelsBeforeSubmissionWhenTheRunCancellationMarkerExists() {
        var queued = task(GenerationTask.Status.QUEUED, false, null);
        var dispatching = task(queued.id(), GenerationTask.Status.DISPATCHING, false, null);
        var cancelled = task(queued.id(), GenerationTask.Status.CANCELLED, false, null);
        var attempt = attempt(queued.id(), GenerationTask.AttemptStatus.REQUESTING);

        when(tasks.find(queued.id())).thenReturn(Mono.just(queued));
        when(tasks.latestAttempt(queued.id())).thenReturn(Mono.just(Optional.empty()));
        when(tasks.nextAttemptNumber(queued.id())).thenReturn(Mono.just(1));
        when(tasks.beginProcessing(eq(queued), any()))
                .thenReturn(Mono.just(new GenerationTaskStore.Processing(dispatching, attempt)));
        when(tasks.cancelled(dispatching)).thenReturn(Mono.just(true));
        when(tasks.markAttemptCancelled(attempt.id())).thenReturn(Mono.just(attempt));
        when(tasks.cancelByProvider(queued.id(), null, null))
                .thenReturn(Mono.just(cancelled));
        when(events.publish(eq(cancelled), eq(GenerationTaskEvents.TASK_CANCELLED), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(queued.id()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(GenerationTask.Status.CANCELLED))
                .verifyComplete();

        verifyNoInteractions(gateway);
    }

    @Test
    void failsWhenTheProviderReportsSuccessWithoutResultAssets() {
        var running = task(GenerationTask.Status.PROVIDER_RUNNING, true, "provider-task-1");
        var failed = task(running.id(), GenerationTask.Status.FAILED, true, "provider-task-1");
        var attempt = attempt(running.id(), GenerationTask.AttemptStatus.RUNNING);
        var route = new GenerationTasks.Route(profile(), gateway);

        when(tasks.find(running.id())).thenReturn(Mono.just(running));
        when(tasks.latestAttempt(running.id()))
                .thenReturn(Mono.just(Optional.of(attempt)));
        when(tasks.cancelled(any())).thenReturn(Mono.just(false));
        when(tasks.routeFor(MODEL_ID, "workspace-a")).thenReturn(Mono.just(route));
        when(gateway.find(any())).thenReturn(Mono.just(new MediaGenerationGateway.State(
                MediaGenerationGateway.Phase.SUCCEEDED,
                "provider-task-1",
                null,
                null,
                null,
                new MediaGenerationGateway.Result(List.of()))));
        when(tasks.fail(
                        eq(running.id()),
                        eq(attempt.id()),
                        any(),
                        eq("generation-provider-result-missing"),
                        anyString(),
                        eq(true),
                        any()))
                .thenReturn(Mono.just(failed));
        when(events.publish(eq(failed), eq(GenerationTaskEvents.TASK_FAILED), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(running.id()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(GenerationTask.Status.FAILED))
                .verifyComplete();

        verifyNoInteractions(storageTransfer);
    }

    private static GenerationTask task(
            GenerationTask.Status status, boolean providerRequestSent, String providerTaskId) {
        return task(UUID.randomUUID(), status, providerRequestSent, providerTaskId);
    }

    private static GenerationTask task(
            UUID id,
            GenerationTask.Status status,
            boolean providerRequestSent,
            String providerTaskId) {
        return new GenerationTask(
                id,
                "alice",
                "workspace-a",
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
                status == GenerationTask.Status.FAILED ? "generation-provider-result-missing" : null,
                status == GenerationTask.Status.FAILED ? "provider returned no assets" : null,
                null,
                "idempotency",
                NOW,
                status == GenerationTask.Status.QUEUED ? null : NOW,
                status.terminal() ? NOW : null);
    }

    private static GenerationTask.Attempt attempt(
            UUID taskId, GenerationTask.AttemptStatus status) {
        return new GenerationTask.Attempt(
                UUID.randomUUID(),
                taskId,
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

    private static GenerationModelProfile profile() {
        return new GenerationModelProfile(
                MODEL_ID,
                "provider-a",
                "provider-model-a",
                UUID.randomUUID(),
                Set.of(GenerationTask.MediaType.IMAGE),
                Set.of(GenerationTask.Operation.TEXT));
    }

    private static MediaGenerationGateway.Submission submission(
            MediaGenerationGateway.Asset asset) {
        return new MediaGenerationGateway.Submission(
                "provider-task-1",
                "provider-request-1",
                UUID.randomUUID(),
                providerState(asset));
    }

    private static MediaGenerationGateway.State providerState(
            MediaGenerationGateway.Asset asset) {
        return new MediaGenerationGateway.State(
                MediaGenerationGateway.Phase.SUCCEEDED,
                "provider-task-1",
                "provider-request-1",
                null,
                null,
                new MediaGenerationGateway.Result(List.of(asset)));
    }

    private static MediaGenerationGateway.Asset providerAsset(String id) {
        Flux<DataBuffer> body = Flux.just(
                DefaultDataBufferFactory.sharedInstance.wrap(new byte[] {1, 2, 3}));
        return new MediaGenerationGateway.Asset(
                id, "result.png", "image/png", 3, body);
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName("creation-results");
        policy.setMetadata(metadata);
        return policy;
    }

    private static StorageFile storageFile() {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("storage-file-1");
        file.setMetadata(metadata);
        file.getSpec().setOriginalName("result.png");
        file.getSpec().setContentType("image/png");
        file.getSpec().setSize(3);
        file.getSpec().setAttributes(Map.of("generationTaskId", "task", "providerAssetId", "provider-asset-1"));
        return file;
    }
}
