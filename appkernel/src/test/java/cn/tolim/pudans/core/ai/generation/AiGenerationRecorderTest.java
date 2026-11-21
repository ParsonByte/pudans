package cn.tolim.pudans.core.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.plan.PlanQuotas;
import cn.tolim.pudans.core.ai.plan.PlanUsageSnapshot;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelCompletion;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGenerationRecorderTest {

    private static final String REQUEST_ID = "gen-1";

    private static final String PROVIDER = "provider-a";

    private static final String MODEL = "model-a";

    @Mock
    AiModelRequestRepository requestRepository;

    @Mock
    PlanQuotas planQuotas;

    @Mock
    AiSettings settingService;

    @Mock
    AiMetrics metrics;

    @Mock
    cn.tolim.pudans.core.storage.StorageTransfer storageTransfer;

    @InjectMocks
    AiGenerationRecorder recorder;

    @Test
    void settlesHeldUnitsAndCompletesWithMediaUsage() {
        var row = row("standard");
        stubBilling();
        when(planQuotas.settle(eq(REQUEST_ID), eq(4500L), any()))
                .thenReturn(Mono.just(new PlanQuotas.SettleResult(REQUEST_ID, 4500, false)));
        when(requestRepository.complete(any())).thenReturn(Mono.just(row));
        var snapshotCaptor = ArgumentCaptor.forClass(PlanUsageSnapshot.class);
        var completionCaptor = ArgumentCaptor.forClass(ModelCompletion.class);

        StepVerifier.create(recorder.succeed(
                        row, model(), false, response(3), AiModelUsage.EMPTY, Instant.now().minusSeconds(2)))
                .assertNext(result -> assertThat(result.requestId()).isEqualTo(REQUEST_ID))
                .verifyComplete();

        verify(planQuotas).settle(eq(REQUEST_ID), eq(4500L), snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().tier()).isEqualTo(AiModel.Tier.HIGH);
        assertThat(snapshotCaptor.getValue().provider()).isEqualTo(PROVIDER);
        verify(requestRepository).complete(completionCaptor.capture());
        var completion = completionCaptor.getValue();
        assertThat(completion.status()).isEqualTo("SUCCEEDED");
        assertThat(completion.units()).isEqualTo(4500L);
        assertThat(completion.imageCount()).isEqualTo(3);
        assertThat(completion.billingStatus()).isEqualTo("SETTLED");
        verify(metrics).generationRequest(PROVIDER, MODEL, "IMAGE_GENERATION", "SUCCEEDED");
        verify(metrics).generationBillingUnits(new BigDecimal("4500"), PROVIDER, MODEL);
    }

    @Test
    void completesWithoutSettlementWhenNoUnitsWereHeld() {
        var row = row(null);
        stubBilling();
        when(requestRepository.complete(any())).thenReturn(Mono.just(row));
        var completionCaptor = ArgumentCaptor.forClass(ModelCompletion.class);

        StepVerifier.create(recorder.succeed(
                        row, model(), false, response(1), AiModelUsage.EMPTY, Instant.now()))
                .expectNextCount(1)
                .verifyComplete();

        verify(planQuotas, never()).settle(anyString(), anyLong(), any());
        verify(requestRepository).complete(completionCaptor.capture());
        assertThat(completionCaptor.getValue().billingStatus()).isEqualTo("NONE");
    }

    @Test
    void releasesHeldUnitsOnFailure() {
        var row = row("standard");
        when(planQuotas.release(REQUEST_ID))
                .thenReturn(Mono.just(new PlanQuotas.ReleaseResult(REQUEST_ID, true)));
        when(requestRepository.complete(any())).thenReturn(Mono.just(row));
        var completionCaptor = ArgumentCaptor.forClass(ModelCompletion.class);

        StepVerifier.create(recorder.fail(row, new IllegalStateException("boom"), Instant.now()))
                .verifyComplete();

        verify(planQuotas).release(REQUEST_ID);
        verify(requestRepository).complete(completionCaptor.capture());
        var completion = completionCaptor.getValue();
        assertThat(completion.status()).isEqualTo("FAILED");
        assertThat(completion.units()).isEqualTo(0L);
        assertThat(completion.billingStatus()).isEqualTo("RELEASED");
        verify(metrics).generationRequest(PROVIDER, MODEL, "IMAGE_GENERATION", "FAILED");
    }

    private void stubBilling() {
        lenient().when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
    }

    private static AiModelResponse response(int images) {
        return new AiModelResponse(
                "upstream-1",
                MODEL,
                List.of(new AiModelContent.Image("https://img.example.com/1.png", null, null, null, null)),
                new AiModelUsage(null, null, null, null, images, null, null, null, null, Map.of()),
                null,
                null,
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private static AiModel model() {
        var model = new AiModel();
        model.getSpec().setModelCode(MODEL);
        model.getSpec().setModelType(AiModel.ModelType.IMAGE);
        return model;
    }

    private static ModelRequestRow row(@org.jspecify.annotations.Nullable String planId) {
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
                "IMAGE",
                "IMAGE_GENERATION",
                PROVIDER,
                "account-a",
                MODEL,
                "HIGH",
                "RUNNING",
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
                null,
                null,
                planId,
                "USER",
                null,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null);
    }
}
