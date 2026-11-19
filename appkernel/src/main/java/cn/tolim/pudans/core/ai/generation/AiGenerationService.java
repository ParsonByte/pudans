package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.model.AiModelUsageEstimator;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.routing.AiRouteResolutionService;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.wallet.AiPointsCalculator;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.NewModelRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiGenerationService {

    private static final String CODE_CAPABILITY_UNSUPPORTED = "ai-generation-capability-unsupported";

    private static final String CODE_MODEL_INVALID = "ai-generation-model-invalid";

    private static final String CODE_ADAPTER_UNSUPPORTED = "ai-generation-adapter-unsupported";

    private static final String CODE_NO_ACCOUNT = "ai-generation-no-account";

    private static final Duration HOLD_TTL = Duration.ofMinutes(10);

    private static final Set<AiModel.Capability> SUPPORTED = Set.of(
            AiModel.Capability.IMAGE_GENERATION,
            AiModel.Capability.IMAGE_EDITING,
            AiModel.Capability.VIDEO_GENERATION,
            AiModel.Capability.SPEECH_RECOGNITION,
            AiModel.Capability.SPEECH_SYNTHESIS,
            AiModel.Capability.AUDIO_GENERATION,
            AiModel.Capability.MUSIC_GENERATION,
            AiModel.Capability.THREE_D_GENERATION,
            AiModel.Capability.OCR,
            AiModel.Capability.VISION,
            AiModel.Capability.AUDIO_UNDERSTANDING,
            AiModel.Capability.VIDEO_UNDERSTANDING);

    private final ReactiveExtensionClient client;

    private final AiProviderAccountService accountService;

    private final AiRouteResolutionService routeResolutionService;

    private final AiModelAdapterRegistry adapterRegistry;

    private final AiModelRequestRepository requestRepository;

    private final AiWalletService walletService;

    private final AiGenerationRecorder recorder;

    private final AiModelTaskScheduler taskScheduler;

    public Mono<AiGenerationResult> generate(AiGenerationCommand command) {
        if (!SUPPORTED.contains(command.capability())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    CODE_CAPABILITY_UNSUPPORTED,
                    "不支持的生成能力：" + command.capability()));
        }
        var idempotencyKey = StringUtils.hasText(command.idempotencyKey())
                ? command.idempotencyKey()
                : "gen-" + UUID.randomUUID();
        return requestRepository
                .findByUserAndIdempotency(command.userId(), idempotencyKey)
                .map(AiGenerationResult::from)
                .switchIfEmpty(Mono.defer(() -> resolve(command)
                        .flatMap(resolved -> execute(command, resolved, idempotencyKey))));
    }

    private Mono<Resolved> resolve(AiGenerationCommand command) {
        if (StringUtils.hasText(command.model())) {
            return client.get(AiModel.class, command.model()).flatMap(model -> {
                if (model.getSpec() == null || !model.getSpec().isEnabled()) {
                    return Mono.error(invalid("模型不存在或已禁用。"));
                }
                if (!model.getSpec().effectiveCapabilities().contains(command.capability())) {
                    return Mono.error(invalid("模型不支持能力：" + command.capability()));
                }
                return providerAndAccount(model)
                        .map(resources -> new Resolved(
                                resources.provider(),
                                resources.account(),
                                model,
                                command.routeGroup(),
                                List.of()));
            });
        }
        return routeResolutionService
                .resolveCapability(new AiRouteResolutionService.CapabilityInput(
                        command.routeGroup(),
                        Set.of(command.capability()),
                        inputModalities(command.inputs()),
                        outputModalities(command.capability()),
                        null,
                        null))
                .flatMap(resolution -> load(resolution.selection())
                        .map(resources -> new Resolved(
                                resources.provider(),
                                resources.account(),
                                resources.model(),
                                resolution.routeGroup().getMetadata().getName(),
                                resolution.plan().fallbackSeats())));
    }

    private Mono<Resources> providerAndAccount(AiModel model) {
        var providerName = model.getSpec().getProvider();
        return client.get(AiProvider.class, providerName)
                .filter(provider -> provider.getSpec() != null && provider.getSpec().isEnabled())
                .switchIfEmpty(Mono.error(invalid("模型所属供应商不可用。")))
                .flatMap(provider -> accountService
                        .firstEnabled(providerName)
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.BAD_GATEWAY, CODE_NO_ACCOUNT, "供应商没有可用账号。")))
                        .map(account -> new Resources(provider, account, model)));
    }

    private Mono<Resources> load(RouteSelection selection) {
        return client.get(AiProvider.class, selection.provider())
                .flatMap(provider -> client.get(AiProviderAccount.class, selection.account())
                        .flatMap(account -> client.get(AiModel.class, selection.model())
                                .map(model -> new Resources(provider, account, model))));
    }

    private Mono<AiGenerationResult> execute(
            AiGenerationCommand command, Resolved resolved, String idempotencyKey) {
        var model = resolved.model();
        var capability = command.capability();
        var requestId = "gen-" + UUID.randomUUID();
        var startedAt = Instant.now();
        var estimate = AiModelUsageEstimator.estimate(capability, command.inputs(), command.params());
        var free = resolved.provider().getSpec().isFree()
                || resolved.account().getSpec().isFreePlan();
        var estimatedPoints = AiPointsCalculator.estimate(model, free, estimate);
        var hold = estimatedPoints.signum() > 0
                ? walletService.freeze(requestId, command.userId(), estimatedPoints, HOLD_TTL).then()
                : Mono.<Void>empty();
        var requestPayload = JsonUtils.objectToJson(Map.of(
                "capability", capability.name(),
                "inputs", command.inputs(),
                "params", command.params(),
                "estimatedUsage", estimate));
        return hold.then(Mono.defer(() -> requestRepository
                        .create(new NewModelRequest(
                                requestId,
                                "default",
                                command.userId(),
                                command.teamId(),
                                command.agentId(),
                                command.sessionId(),
                                idempotencyKey,
                                resolved.routeGroup(),
                                model.getSpec().getModelType().name(),
                                capability.name(),
                                resolved.provider().getMetadata().getName(),
                                resolved.account().getMetadata().getName(),
                                model.getMetadata().getName(),
                                model.getSpec().getTier() == null ? null : model.getSpec().getTier().name(),
                                estimatedPoints,
                                requestPayload))
                        .map(row -> new Execution(row, null))
                        .onErrorResume(DuplicateKeyException.class, error -> replay(
                                command, idempotencyKey, requestId, estimatedPoints))
                        .onErrorResume(error -> releaseHold(requestId, estimatedPoints)
                                .then(Mono.error(error)))
                        .flatMap(execution -> execution.replay() != null
                                ? Mono.just(execution.replay())
                                : invoke(command, resolved, execution.row(), estimate, startedAt))));
    }

    private Mono<Execution> replay(
            AiGenerationCommand command,
            String idempotencyKey,
            String requestId,
            java.math.BigDecimal estimatedPoints) {
        return releaseHold(requestId, estimatedPoints)
                .then(requestRepository.findByUserAndIdempotency(command.userId(), idempotencyKey))
                .map(row -> new Execution(null, AiGenerationResult.from(row)))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "ai-generation-idempotency-conflict",
                        "幂等键已被其他请求占用。")));
    }

    private Mono<Void> releaseHold(String requestId, BigDecimal estimatedPoints) {
        return estimatedPoints.signum() > 0 ? walletService.release(requestId).then() : Mono.empty();
    }

    private Mono<AiGenerationResult> invoke(
            AiGenerationCommand command,
            Resolved resolved,
            ModelRequestRow row,
            AiModelUsage estimate,
            Instant startedAt) {
        return candidates(resolved)
                .flatMap(list -> attempt(command, list, 0, row, estimate, startedAt, null))
                .onErrorResume(error -> recorder.fail(row, error, startedAt).then(Mono.error(error)));
    }

    private Mono<List<Resolved>> candidates(Resolved primary) {
        if (primary.fallbacks().isEmpty()) {
            return Mono.just(List.of(primary));
        }
        return Flux.fromIterable(primary.fallbacks())
                .concatMap(selection -> load(selection)
                        .map(resources -> new Resolved(
                                resources.provider(),
                                resources.account(),
                                resources.model(),
                                primary.routeGroup(),
                                List.of()))
                        .onErrorResume(error -> Mono.empty()))
                .collectList()
                .map(list -> {
                    var all = new ArrayList<Resolved>();
                    all.add(primary);
                    all.addAll(list);
                    return all;
                });
    }

    private Mono<AiGenerationResult> attempt(
            AiGenerationCommand command,
            List<Resolved> candidates,
            int index,
            ModelRequestRow row,
            AiModelUsage estimate,
            Instant startedAt,
            @Nullable Throwable lastError) {
        if (index >= candidates.size()) {
            return Mono.error(lastError == null
                    ? new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "ai-generation-no-candidate",
                            "没有可用的模型候选。")
                    : lastError);
        }
        var candidate = candidates.get(index);
        var protocol = candidate.provider().getSpec().getProtocol();
        if (!adapterRegistry.supports(protocol, command.capability())) {
            return attempt(
                    command,
                    candidates,
                    index + 1,
                    row,
                    estimate,
                    startedAt,
                    new ApiException(
                            HttpStatus.BAD_REQUEST,
                            CODE_ADAPTER_UNSUPPORTED,
                            "供应商协议 " + protocol + " 不支持能力 " + command.capability()));
        }
        return invokeOnce(command, candidate, row, estimate, startedAt)
                .onErrorResume(CandidateFailure.class, failure -> attempt(
                        command, candidates, index + 1, row, estimate, startedAt, failure.getCause()));
    }

    private Mono<AiGenerationResult> invokeOnce(
            AiGenerationCommand command,
            Resolved resolved,
            ModelRequestRow row,
            AiModelUsage estimate,
            Instant startedAt) {
        var model = resolved.model();
        var capability = command.capability();
        var adapter = adapterRegistry.require(
                resolved.provider().getSpec().getProtocol(), capability);
        var free = resolved.provider().getSpec().isFree()
                || resolved.account().getSpec().isFreePlan();
        var request = new AiModelRequest(
                capability,
                command.inputs(),
                command.params(),
                false,
                row.idempotencyKey());
        return Mono.defer(() -> adapter
                        .invoke(resolved.provider(), resolved.account(), model, request)
                        .switchIfEmpty(Mono.error(new IllegalStateException("供应商返回空响应。")))
                        .onErrorResume(error -> Mono.error(new CandidateFailure(error))))
                .flatMap(response -> {
                    if (response.pending()) {
                        if (!StringUtils.hasText(response.providerTaskId())) {
                            return Mono.error(new CandidateFailure(
                                    new IllegalStateException("供应商任务缺少任务 ID。")));
                        }
                        return requestRepository
                                .markTask(row.requestId(), response.providerTaskId())
                                .flatMap(updated -> taskScheduler.enqueuePoll(updated)
                                        .thenReturn(AiGenerationResult.from(updated)));
                    }
                    if (response.status() == AiModelResponse.TaskStatus.FAILED
                            || response.status() == AiModelResponse.TaskStatus.CANCELLED) {
                        return Mono.error(new CandidateFailure(new ApiException(
                                HttpStatus.BAD_GATEWAY,
                                "ai-generation-provider-failed",
                                "供应商生成失败。")));
                    }
                    return recorder.succeed(row, model, free, response, estimate, startedAt);
                });
    }

    private static Set<AiModel.Modality> inputModalities(List<AiModelContent> inputs) {
        var modalities = java.util.EnumSet.noneOf(AiModel.Modality.class);
        for (var input : inputs) {
            if (input instanceof AiModelContent.Text) {
                modalities.add(AiModel.Modality.TEXT);
            } else if (input instanceof AiModelContent.Image) {
                modalities.add(AiModel.Modality.IMAGE);
            } else if (input instanceof AiModelContent.Audio) {
                modalities.add(AiModel.Modality.AUDIO);
            } else if (input instanceof AiModelContent.Video) {
                modalities.add(AiModel.Modality.VIDEO);
            } else if (input instanceof AiModelContent.File) {
                modalities.add(AiModel.Modality.FILE);
            } else if (input instanceof AiModelContent.ThreeD) {
                modalities.add(AiModel.Modality.THREE_D);
            }
        }
        return modalities.isEmpty() ? Set.of(AiModel.Modality.TEXT) : modalities;
    }

    private static Set<AiModel.Modality> outputModalities(AiModel.Capability capability) {
        return switch (capability) {
            case IMAGE_GENERATION, IMAGE_EDITING -> Set.of(AiModel.Modality.IMAGE);
            case VIDEO_GENERATION -> Set.of(AiModel.Modality.VIDEO);
            case SPEECH_SYNTHESIS, AUDIO_GENERATION, MUSIC_GENERATION -> Set.of(AiModel.Modality.AUDIO);
            case SPEECH_RECOGNITION, OCR, VISION, AUDIO_UNDERSTANDING, VIDEO_UNDERSTANDING ->
                Set.of(AiModel.Modality.TEXT);
            case THREE_D_GENERATION -> Set.of(AiModel.Modality.THREE_D);
            default -> Set.of();
        };
    }

    private static ApiException invalid(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_MODEL_INVALID, reason);
    }

    private record Resolved(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            String routeGroup,
            List<RouteSelection> fallbacks) {}

    private record Execution(@Nullable ModelRequestRow row, @Nullable AiGenerationResult replay) {}

    private record Resources(AiProvider provider, AiProviderAccount account, AiModel model) {}

    private static final class CandidateFailure extends RuntimeException {

        CandidateFailure(Throwable cause) {
            super(cause);
        }
    }
}
