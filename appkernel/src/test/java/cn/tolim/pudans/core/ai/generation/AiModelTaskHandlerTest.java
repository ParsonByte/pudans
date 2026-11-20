package cn.tolim.pudans.core.ai.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.model.AiModelAdapter;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiModelTaskHandlerTest {

    private static final String REQUEST_ID = "gen-1";

    private static final String PROVIDER = "provider-a";

    private static final String ACCOUNT = "account-a";

    private static final String MODEL = "model-a";

    @Mock
    AiModelRequestRepository requestRepository;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiModelAdapterRegistry adapterRegistry;

    @Mock
    AiGenerationRecorder recorder;

    @Mock
    AiModelTaskScheduler scheduler;

    @Mock
    AiModelAdapter adapter;

    @InjectMocks
    AiModelTaskHandler handler;

    @Test
    void reschedulesPendingTasks() {
        var row = row("RUNNING", "task-1");
        stubLoad(row);
        when(adapter.poll(
                        any(), any(), any(), eq(AiModel.Capability.VIDEO_GENERATION),
                        eq("task-1"), any()))
                .thenReturn(Mono.just(new AiModelResponse(
                        null,
                        MODEL,
                        List.of(),
                        AiModelUsage.EMPTY,
                        null,
                        "task-1",
                        AiModelResponse.TaskStatus.RUNNING)));
        when(scheduler.reschedule(any())).thenReturn(Mono.just(true));

        StepVerifier.create(handler.handle(task())).verifyComplete();

        verify(scheduler).reschedule(any(AiTask.class));
        verify(scheduler, never()).enqueuePoll(any());
        verifyNoInteractions(recorder);
    }

    @Test
    void failsRequestsThatExceedThePollingWindow() {
        var row = row(
                "RUNNING",
                "task-1",
                Instant.now().minus(Duration.ofHours(3)));
        when(requestRepository.findByRequestId(REQUEST_ID)).thenReturn(Mono.just(row));
        when(recorder.fail(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task())).verifyComplete();

        verify(recorder).fail(eq(row), any(IllegalStateException.class), eq(row.createdAt()));
        verifyNoInteractions(adapterRegistry);
    }

    @Test
    void settlesCompletedTasks() {
        var row = row("RUNNING", "task-1");
        stubLoad(row);
        var response = new AiModelResponse(
                "task-1",
                MODEL,
                List.of(),
                AiModelUsage.EMPTY,
                null,
                "task-1",
                AiModelResponse.TaskStatus.SUCCEEDED);
        when(adapter.poll(
                        any(), any(), any(), eq(AiModel.Capability.VIDEO_GENERATION),
                        eq("task-1"), any()))
                .thenReturn(Mono.just(response));
        when(recorder.succeed(any(), any(), eq(false), any(), any(), any()))
                .thenReturn(Mono.just(result()));

        StepVerifier.create(handler.handle(task())).verifyComplete();

        verify(recorder).succeed(eq(row), any(AiModel.class), eq(false), eq(response), any(), any());
        verify(recorder, never()).fail(any(), any(), any());
    }

    @Test
    void recordsFailedProviderTasks() {
        var row = row("RUNNING", "task-1");
        stubLoad(row);
        when(adapter.poll(
                        any(), any(), any(), eq(AiModel.Capability.VIDEO_GENERATION),
                        eq("task-1"), any()))
                .thenReturn(Mono.just(new AiModelResponse(
                        null,
                        MODEL,
                        List.of(),
                        AiModelUsage.EMPTY,
                        "provider failed",
                        "task-1",
                        AiModelResponse.TaskStatus.FAILED)));
        when(recorder.fail(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task())).verifyComplete();

        verify(recorder).fail(eq(row), any(IllegalStateException.class), any());
        verify(recorder, never()).succeed(any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void recordsCancelledProviderTasksAsFailures() {
        var row = row("RUNNING", "task-1");
        stubLoad(row);
        when(adapter.poll(
                        any(), any(), any(), eq(AiModel.Capability.VIDEO_GENERATION),
                        eq("task-1"), any()))
                .thenReturn(Mono.just(new AiModelResponse(
                        null,
                        MODEL,
                        List.of(),
                        AiModelUsage.EMPTY,
                        "cancelled",
                        "task-1",
                        AiModelResponse.TaskStatus.CANCELLED)));
        when(recorder.fail(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task())).verifyComplete();

        verify(recorder).fail(eq(row), any(IllegalStateException.class), any());
        verify(recorder, never()).succeed(any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void failsTheRequestWhenPollingBreaks() {
        var row = row("RUNNING", "task-1");
        stubLoad(row);
        when(adapter.poll(
                        any(), any(), any(), eq(AiModel.Capability.VIDEO_GENERATION),
                        eq("task-1"), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));
        when(recorder.fail(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task())).verifyComplete();

        verify(recorder).fail(any(), any(IllegalStateException.class), any());
    }

    @Test
    void ignoresTasksForCompletedRequests() {
        when(requestRepository.findByRequestId(REQUEST_ID))
                .thenReturn(Mono.just(row("SUCCEEDED", "task-1")));

        StepVerifier.create(handler.handle(task())).verifyComplete();

        verifyNoInteractions(adapterRegistry, recorder, scheduler);
    }

    @Test
    void ignoresTasksWithoutRequestId() {
        var task = new AiTask(
                1L,
                AiModelTaskScheduler.TASK_TYPE,
                "ai-model-poll:" + REQUEST_ID,
                "default",
                "user-1",
                null,
                null,
                null,
                null,
                0,
                Map.of(),
                0,
                1,
                Instant.now(),
                Instant.now(),
                Instant.now());

        StepVerifier.create(handler.handle(task)).verifyComplete();

        verifyNoInteractions(requestRepository, adapterRegistry, recorder, scheduler);
    }

    private void stubLoad(ModelRequestRow row) {
        when(requestRepository.findByRequestId(REQUEST_ID)).thenReturn(Mono.just(row));
        when(client.get(AiProvider.class, PROVIDER)).thenReturn(Mono.just(provider()));
        when(client.get(AiProviderAccount.class, ACCOUNT)).thenReturn(Mono.just(account()));
        when(client.get(AiModel.class, MODEL)).thenReturn(Mono.just(model()));
        when(adapterRegistry.require(AiProvider.Protocol.ANTHROPIC, AiModel.Capability.VIDEO_GENERATION))
                .thenReturn(adapter);
    }

    private static AiTask task() {
        return new AiTask(
                1L,
                AiModelTaskScheduler.TASK_TYPE,
                "ai-model-poll:" + REQUEST_ID,
                "default",
                "user-1",
                null,
                null,
                null,
                null,
                0,
                Map.of("requestId", REQUEST_ID),
                0,
                1,
                Instant.now(),
                Instant.now(),
                Instant.now());
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(AiProvider.Protocol.ANTHROPIC);
        provider.getSpec().setEnabled(true);
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName(ACCOUNT);
        account.setMetadata(metadata);
        account.getSpec().setProvider(PROVIDER);
        account.getSpec().setEnabled(true);
        return account;
    }

    private static AiModel model() {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(MODEL);
        model.setMetadata(metadata);
        model.getSpec().setProvider(PROVIDER);
        model.getSpec().setModelCode("video-01");
        model.getSpec().setModelType(AiModel.ModelType.VIDEO);
        return model;
    }

    private static ModelRequestRow row(String status, String providerTaskId) {
        return row(status, providerTaskId, Instant.now());
    }

    private static ModelRequestRow row(
            String status, String providerTaskId, Instant createdAt) {
        return new ModelRequestRow(
                1L,
                REQUEST_ID,
                "default",
                "user-1",
                null,
                null,
                null,
                "idem-1",
                "default",
                "VIDEO",
                "VIDEO_GENERATION",
                PROVIDER,
                ACCOUNT,
                MODEL,
                "HIGH",
                status,
                null,
                null,
                providerTaskId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "USER",
                null,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                null);
    }

    private static AiGenerationResult result() {
        return new AiGenerationResult(
                REQUEST_ID,
                "SUCCEEDED",
                "VIDEO",
                "VIDEO_GENERATION",
                PROVIDER,
                ACCOUNT,
                MODEL,
                List.of(),
                AiModelUsage.EMPTY,
                null,
                null,
                "SETTLED",
                "task-1",
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
