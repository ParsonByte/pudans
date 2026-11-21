package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiPlan;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.model.AiModelUsageEstimator;
import cn.tolim.pudans.core.ai.plan.PlanCatalog;
import cn.tolim.pudans.core.ai.plan.PlanQuotas;
import cn.tolim.pudans.core.ai.plan.PlanResolution;
import cn.tolim.pudans.core.ai.plan.UsageUnitCalculator;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.ProviderSpendGuard;
import cn.tolim.pudans.core.ai.routing.RouteResolution;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitFailure;
import cn.tolim.pudans.core.ai.provider.ProviderProtocols;
import cn.tolim.pudans.core.ai.team.TeamQuotas;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.NewModelRequest;
import java.math.BigDecimal;
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
public class Generations {

    private static final String CODE_CAPABILITY_UNSUPPORTED = "ai-generation-capability-unsupported";

    private static final String CODE_MODEL_INVALID = "ai-generation-model-invalid";

    private static final String CODE_ADAPTER_UNSUPPORTED = "ai-generation-adapter-unsupported";

    private static final String CODE_NO_ACCOUNT = "ai-generation-no-account";

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

    private final ProviderAccounts providerAccounts;

    private final RouteResolution routeResolution;

    private final AiModelAdapterRegistry adapterRegistry;

    private final AiModelRequestRepository requestRepository;

    private final PlanResolution planResolution;

    private final PlanQuotas planQuotas;

    private final AiSettings aiSettings;

    private final AiGenerationRecorder recorder;

    private final AiModelTaskScheduler taskScheduler;

    private final CircuitBreaker circuitBreaker;

    private final TeamQuotas teamQuotas;

    private final Teams teams;

    private final ProviderSpendGuard providerSpendGuard;

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
        return validateTeam(command)
                .then(Mono.defer(() -> requestRepository
                        .findByUserAndIdempotency(command.userId(), idempotencyKey)
                        .map(AiGenerationResult::from)
                        .switchIfEmpty(Mono.defer(() -> resolve(command)
                                .flatMap(resolved -> execute(command, resolved, idempotencyKey))))));
    }

    private Mono<Void> validateTeam(AiGenerationCommand command) {
        var teamId = command.teamId();
        if (!StringUtils.hasText(teamId)) {
            return Mono.empty();
        }
        if (StringUtils.hasText(command.agentId())) {
            return teams.requireAgent(command.agentId(), command.userId())
                    .flatMap(agent -> teamId.equals(agent.getSpec().getTeam())
                            ? Mono.<Void>empty()
                            : Mono.error(new ApiException(
                                    HttpStatus.FORBIDDEN,
                                    "ai-generation-team-forbidden",
                                    "生成请求的团队与 Agent 所属团队不一致。")));
        }
        return teams.isMember(teamId, command.userId())
                .flatMap(member -> Boolean.TRUE.equals(member)
                        ? Mono.<Void>empty()
                        : Mono.error(new ApiException(
                                HttpStatus.FORBIDDEN,
                                "ai-generation-team-forbidden",
                                "无权使用该团队。")));
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
        return routeResolution
                .resolveCapability(new RouteResolution.CapabilityInput(
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
                .flatMap(provider -> providerAccounts
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
        return aiSettings
                .spec()
                .map(spec -> spec.getBilling() == null ? new AiSetting.Billing() : spec.getBilling())
                .defaultIfEmpty(new AiSetting.Billing())
                .flatMap(billing -> plan(command)
                        .flatMap(plan -> execute(command, resolved, idempotencyKey, billing, plan)));
    }

    // SYSTEM 调用不解析套餐，也不占用用户窗口和团队配额
    private Mono<PlanContext> plan(AiGenerationCommand command) {
        if (command.billingScope() == AiPlan.BillingScope.SYSTEM) {
            return Mono.just(PlanContext.SYSTEM);
        }
        return planResolution.resolve(command.userId()).map(PlanContext::new);
    }

    private Mono<AiGenerationResult> execute(
            AiGenerationCommand command,
            Resolved resolved,
            String idempotencyKey,
            AiSetting.Billing billing,
            PlanContext plan) {
        var model = resolved.model();
        var capability = command.capability();
        var requestId = "gen-" + UUID.randomUUID();
        var startedAt = Instant.now();
        var estimate = AiModelUsageEstimator.estimate(capability, command.inputs(), command.params());
        var free = resolved.provider().getSpec().isFree()
                || resolved.account().getSpec().isFreePlan();
        var estimatedUnits = new UsageUnitCalculator(billing, free)
                .charge(estimate, model.getSpec().getTier(), model.getSpec().getModelCode());
        var config = plan.plan();
        var reserved = config != null && estimatedUnits.signum() > 0;
        var planId = reserved && config.getMetadata() != null ? config.getMetadata().getName() : null;
        var guard = providerSpendGuard
                .check(resolved.provider(), resolved.account(), estimatedUnits)
                .then(config == null ? Mono.empty() : teamQuotas.check(command.teamId(), estimatedUnits));
        var hold = guard.then(reserved
                ? planQuotas
                        .reserve(
                                requestId,
                                command.userId(),
                                config,
                                estimatedUnits.longValue(),
                                "GENERATION",
                                command.teamId(),
                                requestId)
                        .then()
                : Mono.<Void>empty());
        var requestPayload = JsonUtils.objectToJson(Map.of(
                "capability", capability.name(),
                "inputs", command.inputs(),
                "params", command.params(),
                "estimatedUsage", estimate));
        return requireFeature(plan)
                .then(hold)
                .then(Mono.defer(() -> requestRepository
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
                                planId,
                                command.billingScope().name(),
                                requestPayload))
                        .map(row -> new Execution(row, null))
                        .onErrorResume(DuplicateKeyException.class, error -> replay(
                                command, idempotencyKey, requestId, estimatedUnits, config))
                        .onErrorResume(error -> releaseHold(requestId, estimatedUnits, config)
                                .then(Mono.error(error)))
                        .flatMap(execution -> execution.replay() != null
                                ? Mono.just(execution.replay())
                                : invoke(command, resolved, execution.row(), estimate, startedAt, plan))));
    }

    private Mono<Execution> replay(
            AiGenerationCommand command,
            String idempotencyKey,
            String requestId,
            BigDecimal estimatedUnits,
            @Nullable AiPlan plan) {
        return releaseHold(requestId, estimatedUnits, plan)
                .then(requestRepository.findByUserAndIdempotency(command.userId(), idempotencyKey))
                .map(row -> new Execution(null, AiGenerationResult.from(row)))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "ai-generation-idempotency-conflict",
                        "幂等键已被其他请求占用。")));
    }

    private Mono<Void> releaseHold(
            String requestId, BigDecimal estimatedUnits, @Nullable AiPlan plan) {
        return plan == null || estimatedUnits.signum() <= 0
                ? Mono.empty()
                : planQuotas.release(requestId).then();
    }

    private Mono<AiGenerationResult> invoke(
            AiGenerationCommand command,
            Resolved resolved,
            ModelRequestRow row,
            AiModelUsage estimate,
            Instant startedAt,
            PlanContext plan) {
        return candidates(resolved, plan)
                .flatMap(list -> attempt(command, list, 0, row, estimate, startedAt, null))
                .onErrorResume(error -> recorder.fail(row, error, startedAt).then(Mono.error(error)));
    }

    private Mono<List<Resolved>> candidates(Resolved primary, PlanContext plan) {
        var candidates = primary.fallbacks().isEmpty()
                ? Mono.just(List.of(primary))
                : Flux.fromIterable(primary.fallbacks())
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
        return candidates
                .map(list -> list.stream().filter(candidate -> planAllows(plan, candidate)).toList())
                .flatMap(list -> list.isEmpty()
                        ? Mono.error(planDenied(primary.model().getMetadata().getName()))
                        : Mono.just(list));
    }

    private static boolean planAllows(PlanContext plan, Resolved candidate) {
        var config = plan.plan();
        if (config == null || config.getSpec() == null) {
            return true;
        }
        var access = config.getSpec().getAccess() == null
                ? new AiPlan.Access()
                : config.getSpec().getAccess();
        var modelName = candidate.model().getMetadata() == null
                ? null
                : candidate.model().getMetadata().getName();
        var allowedGroup = candidate.routeGroup() == null || access.allowsRouteGroup(candidate.routeGroup());
        return allowedGroup && access.allowsModel(modelName);
    }

    private static Mono<Void> requireFeature(PlanContext plan) {
        var config = plan.plan();
        if (config == null || config.getSpec() == null) {
            return Mono.empty();
        }
        var access = config.getSpec().getAccess() == null
                ? new AiPlan.Access()
                : config.getSpec().getAccess();
        if (!access.allowsFeature(AiPlan.Feature.CHAT)) {
            return Mono.error(planDenied(null));
        }
        return Mono.empty();
    }

    private static ApiException planDenied(@Nullable String model) {
        var reason = model == null
                ? "当前套餐不允许使用生成功能。"
                : "当前套餐不允许使用模型 " + model + "。";
        return new ApiException(HttpStatus.FORBIDDEN, AiPlan.CODE_MODEL_DENIED, reason);
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
        if (circuitOpen(candidate)) {
            return attempt(
                    command,
                    candidates,
                    index + 1,
                    row,
                    estimate,
                    startedAt,
                    new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "ai-generation-circuit-open",
                            "供应商当前处于熔断冷却中。"));
        }
        var protocol = ProviderProtocols.of(candidate.provider(), candidate.account());
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
                .onErrorResume(CandidateFailure.class, failure -> {
                    recordFailure(candidate);
                    return attempt(
                            command, candidates, index + 1, row, estimate, startedAt, failure.getCause());
                });
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
                ProviderProtocols.of(resolved.provider(), resolved.account()), capability);
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
                        recordSuccess(resolved);
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
                    recordSuccess(resolved);
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

    private boolean circuitOpen(Resolved candidate) {
        return circuitBreaker.isOpen(candidate.provider().getMetadata().getName())
                || circuitBreaker.isOpen(candidate.account().getMetadata().getName());
    }

    private void recordSuccess(Resolved candidate) {
        circuitBreaker.recordSuccess(candidate.provider().getMetadata().getName());
        circuitBreaker.recordSuccess(candidate.account().getMetadata().getName());
    }

    private void recordFailure(Resolved candidate) {
        circuitBreaker.recordFailure(
                candidate.provider().getMetadata().getName(), CircuitFailure.defaultFailure());
        circuitBreaker.recordFailure(
                candidate.account().getMetadata().getName(), CircuitFailure.defaultFailure());
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

    private record PlanContext(PlanResolution.@Nullable ResolvedPlan resolved) {

        private static final PlanContext SYSTEM = new PlanContext(null);

        private @Nullable AiPlan plan() {
            return resolved == null ? null : resolved.plan();
        }
    }

    private record Resources(AiProvider provider, AiProviderAccount account, AiModel model) {}

    private static final class CandidateFailure extends RuntimeException {

        CandidateFailure(Throwable cause) {
            super(cause);
        }
    }
}
