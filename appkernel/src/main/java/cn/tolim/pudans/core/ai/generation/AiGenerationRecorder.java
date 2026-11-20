package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.wallet.AiPointsCalculator;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.core.ai.wallet.AiWalletUsage;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelCompletion;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiGenerationRecorder {

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    private static final String STATUS_FAILED = "FAILED";

    private static final String BILLING_SETTLED = "SETTLED";

    private static final String BILLING_RELEASED = "RELEASED";

    private static final String BILLING_NONE = "NONE";

    private static final String SOURCE_PROVIDER = "PROVIDER";

    private static final String SOURCE_ESTIMATED = "ESTIMATED";

    private static final String ERROR_FAILED = "ai-generation-failed";

    private final AiModelRequestRepository requestRepository;

    private final AiWalletService walletService;

    private final AiMetrics metrics;

    private final StorageService storageService;

    public Mono<AiGenerationResult> succeed(
            ModelRequestRow row,
            AiModel model,
            boolean free,
            AiModelResponse response,
            AiModelUsage fallbackUsage,
            Instant startedAt) {
        var usage = response.usage() != null && response.usage().hasAny() ? response.usage() : fallbackUsage;
        var points = AiPointsCalculator.charge(model, free, usage);
        metrics.generationRequest(
                row.selectedProvider(), row.selectedModel(), row.capability(), STATUS_SUCCEEDED);
        metrics.generationUsage(
                row.selectedProvider(), row.selectedModel(), row.capability(), usage);
        metrics.generationPoints(points, row.selectedProvider(), row.selectedModel());
        var hold = row.holdPoints();
        var hasHold = hold != null && hold.signum() > 0;
        var billingSource = response.usage() != null && response.usage().hasAny()
                ? SOURCE_PROVIDER
                : SOURCE_ESTIMATED;
        var settle = hasHold
                ? walletService
                        .settle(
                                row.requestId(),
                                row.userId(),
                                hold,
                                points,
                                walletUsage(row, usage, billingSource))
                        .then()
                : Mono.<Void>empty();
        var latency = latency(startedAt);
        return persistOutputs(response.outputs(), row.userId()).flatMap(outputs -> {
            var resultPayload = JsonUtils.objectToJson(Map.of("outputs", outputs));
            var completion = new ModelCompletion(
                    row.requestId(),
                    STATUS_SUCCEEDED,
                    null,
                    null,
                    response.providerTaskId() != null
                            ? response.providerTaskId()
                            : row.providerTaskId(),
                    usage.inputTokens(),
                    usage.cacheReadTokens(),
                    usage.outputTokens(),
                    usage.imageCount(),
                    usage.videoSeconds(),
                    usage.audioSeconds(),
                    usage.speechCharacters(),
                    usage.taskCount(),
                    points,
                    billingSource,
                    hasHold ? BILLING_SETTLED : BILLING_NONE,
                    latency,
                    billingSource,
                    response.id(),
                    resultPayload);
            return settle.then(requestRepository.complete(completion))
                    .map(AiGenerationResult::from);
        });
    }

    private Mono<List<AiModelContent>> persistOutputs(
            List<AiModelContent> outputs, String userId) {
        return Flux.fromIterable(outputs)
                .concatMap(content -> persistOutput(content, userId))
                .collectList();
    }

    private Mono<AiModelContent> persistOutput(AiModelContent content, String userId) {
        var data = dataOf(content);
        if (!StringUtils.hasText(data)) {
            return Mono.just(content);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException error) {
            return Mono.just(content);
        }
        var mediaType = mediaTypeOf(content);
        var request = new StorageWriteRequest(
                null,
                userId,
                "ai-generation",
                null,
                "generated-" + UUID.randomUUID() + extension(mediaType),
                mediaType,
                bytes.length,
                Map.of("purpose", "ai-generation"),
                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)),
                false);
        return storageService
                .upload(request)
                .map(file -> withFileId(content, file.getMetadata().getName()))
                .onErrorResume(error -> Mono.just(content));
    }

    private static @Nullable String dataOf(AiModelContent content) {
        return switch (content) {
            case AiModelContent.Image image -> image.data();
            case AiModelContent.Audio audio -> audio.data();
            case AiModelContent.Video video -> video.data();
            case AiModelContent.File file -> file.data();
            case AiModelContent.ThreeD threeD -> threeD.data();
            default -> null;
        };
    }

    private static String mediaTypeOf(AiModelContent content) {
        var mediaType = switch (content) {
            case AiModelContent.Image image -> image.mediaType();
            case AiModelContent.Audio audio -> audio.mediaType();
            case AiModelContent.Video video -> video.mediaType();
            case AiModelContent.File file -> file.mediaType();
            case AiModelContent.ThreeD threeD -> threeD.mediaType();
            default -> null;
        };
        return StringUtils.hasText(mediaType) ? mediaType : "application/octet-stream";
    }

    private static String extension(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "video/mp4" -> ".mp4";
            default -> "";
        };
    }

    private static AiModelContent withFileId(AiModelContent content, String fileId) {
        return switch (content) {
            case AiModelContent.Image image -> new AiModelContent.Image(
                    image.url(), null, image.mediaType(), image.detail(), fileId);
            case AiModelContent.Audio audio -> new AiModelContent.Audio(
                    audio.url(), null, audio.mediaType(), audio.durationSeconds(),
                    audio.format(), fileId);
            case AiModelContent.Video video -> new AiModelContent.Video(
                    video.url(), null, video.mediaType(), video.durationSeconds(), fileId);
            case AiModelContent.File file -> new AiModelContent.File(
                    file.url(), null, file.filename(), file.mediaType(), fileId);
            case AiModelContent.ThreeD threeD -> new AiModelContent.ThreeD(
                    threeD.url(), null, threeD.mediaType(), fileId);
            default -> content;
        };
    }

    public Mono<Void> fail(ModelRequestRow row, Throwable error, Instant startedAt) {
        metrics.generationRequest(
                row.selectedProvider(), row.selectedModel(), row.capability(), STATUS_FAILED);
        var hold = row.holdPoints();
        var hasHold = hold != null && hold.signum() > 0;
        var release = hasHold ? walletService.release(row.requestId()).then() : Mono.<Void>empty();
        var code = error instanceof ApiException apiException ? apiException.getCode() : ERROR_FAILED;
        var message = error instanceof ApiException apiException && apiException.getReason() != null
                ? apiException.getReason()
                : "AI 模型调用失败。";
        var completion = new ModelCompletion(
                row.requestId(),
                STATUS_FAILED,
                code,
                message,
                row.providerTaskId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                hasHold ? BILLING_RELEASED : BILLING_NONE,
                latency(startedAt),
                null,
                null,
                "{}");
        return release.then(requestRepository.complete(completion)).then();
    }

    private static AiWalletUsage walletUsage(
            ModelRequestRow row, AiModelUsage usage, String billingSource) {
        return new AiWalletUsage(
                row.selectedAccount(),
                row.selectedModel(),
                row.tier(),
                usage.inputTokens(),
                usage.cacheReadTokens(),
                usage.outputTokens(),
                null,
                billingSource,
                usage.imageCount(),
                usage.videoSeconds(),
                usage.audioSeconds(),
                usage.speechCharacters(),
                usage.taskCount(),
                usage.extra());
    }

    private static int latency(@Nullable Instant startedAt) {
        if (startedAt == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
    }
}
