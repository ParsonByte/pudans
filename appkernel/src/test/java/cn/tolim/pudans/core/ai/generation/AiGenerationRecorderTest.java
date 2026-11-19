package cn.tolim.pudans.core.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.core.ai.wallet.AiWalletService.ReleaseResult;
import cn.tolim.pudans.core.ai.wallet.AiWalletService.SettleResult;
import cn.tolim.pudans.core.ai.wallet.AiWalletUsage;
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
    AiWalletService walletService;

    @Mock
    AiMetrics metrics;

    @InjectMocks
    AiGenerationRecorder recorder;

    @Test
    void settlesHeldPointsAndCompletesWithMediaUsage() {
        var row = row(new BigDecimal("10"));
        when(walletService.settle(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.just(new SettleResult(REQUEST_ID, "user-1", new BigDecimal("6"),
                        BigDecimal.ZERO, "settle-gen-1", false)));
        when(requestRepository.complete(any())).thenReturn(Mono.just(row));
        var usageCaptor = ArgumentCaptor.forClass(AiWalletUsage.class);
        var completionCaptor = ArgumentCaptor.forClass(ModelCompletion.class);

        StepVerifier.create(recorder.succeed(
                        row, model(), false, response(3), AiModelUsage.EMPTY, Instant.now().minusSeconds(2)))
                .assertNext(result -> assertThat(result.requestId()).isEqualTo(REQUEST_ID))
                .verifyComplete();

        verify(walletService).settle(
                eq(REQUEST_ID), eq("user-1"), eq(new BigDecimal("10")), eq(new BigDecimal("6")),
                usageCaptor.capture());
        assertThat(usageCaptor.getValue().imageCount()).isEqualTo(3);
        verify(requestRepository).complete(completionCaptor.capture());
        var completion = completionCaptor.getValue();
        assertThat(completion.status()).isEqualTo("SUCCEEDED");
        assertThat(completion.points()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(completion.imageCount()).isEqualTo(3);
        assertThat(completion.billingStatus()).isEqualTo("SETTLED");
        verify(metrics).generationRequest(PROVIDER, MODEL, "IMAGE_GENERATION", "SUCCEEDED");
        verify(metrics).generationPoints(new BigDecimal("6"), PROVIDER, MODEL);
    }

    @Test
    void completesWithoutSettlementWhenNoPointsWereHeld() {
        var row = row(null);
        when(requestRepository.complete(any())).thenReturn(Mono.just(row));
        var completionCaptor = ArgumentCaptor.forClass(ModelCompletion.class);

        StepVerifier.create(recorder.succeed(
                        row, model(), false, response(1), AiModelUsage.EMPTY, Instant.now()))
                .expectNextCount(1)
                .verifyComplete();

        verify(walletService, never()).settle(anyString(), anyString(), any(), any(), any());
        verify(requestRepository).complete(completionCaptor.capture());
        assertThat(completionCaptor.getValue().billingStatus()).isEqualTo("NONE");
    }

    @Test
    void releasesHeldPointsOnFailure() {
        var row = row(new BigDecimal("10"));
        when(walletService.release(REQUEST_ID))
                .thenReturn(Mono.just(new ReleaseResult(REQUEST_ID, true, "release-gen-1")));
        when(requestRepository.complete(any())).thenReturn(Mono.just(row));
        var completionCaptor = ArgumentCaptor.forClass(ModelCompletion.class);

        StepVerifier.create(recorder.fail(row, new IllegalStateException("boom"), Instant.now()))
                .verifyComplete();

        verify(walletService).release(REQUEST_ID);
        verify(requestRepository).complete(completionCaptor.capture());
        var completion = completionCaptor.getValue();
        assertThat(completion.status()).isEqualTo("FAILED");
        assertThat(completion.points()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(completion.billingStatus()).isEqualTo("RELEASED");
        verify(metrics).generationRequest(PROVIDER, MODEL, "IMAGE_GENERATION", "FAILED");
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
        model.getSpec().setImagePointsPerItem(2);
        return model;
    }

    private static ModelRequestRow row(BigDecimal hold) {
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
                hold,
                null,
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
