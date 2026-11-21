package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.conversation.Conversations;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent.Delta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent.Done;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent.Error;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent.Meta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayEvent.Usage;
import cn.tolim.pudans.core.ai.gateway.agent.AiAgentAdapterRegistry;
import cn.tolim.pudans.core.ai.gateway.agent.AiRequestKind;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.session.GatewaySessions;
import cn.tolim.pudans.core.ai.gateway.session.GatewaySessions.BindRequest;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.plan.PlanCatalog;
import cn.tolim.pudans.core.ai.plan.PlanQuotaExceededException;
import cn.tolim.pudans.core.ai.plan.PlanQuotas;
import cn.tolim.pudans.core.ai.plan.PlanResolution;
import cn.tolim.pudans.core.ai.plan.PlanUsageSnapshot;
import cn.tolim.pudans.core.ai.plan.PlanWindowKind;
import cn.tolim.pudans.core.ai.plan.UsageUnitCalculator;
import cn.tolim.pudans.core.ai.provider.ProviderSpendGuard;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChat;
import cn.tolim.pudans.core.ai.routing.RouteResolution;
import cn.tolim.pudans.core.ai.routing.RouteRequest;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.RouteTrace;
import cn.tolim.pudans.core.ai.routing.SunkRouteRegistry;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitFailure;
import cn.tolim.pudans.core.ai.team.TeamQuotas;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository.ChatCompletion;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository.NewChatRequest;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.NewConversation;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGateway {

    private static final String STATUS_RUNNING = "RUNNING";

    private static final String AGENT_SOURCE_CHAT = "pudans-chat";

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    private static final String STATUS_FAILED = "FAILED";

    private static final String BILLING_SETTLED = "SETTLED";

    private static final String BILLING_RELEASED = "RELEASED";

    private static final String SOURCE_PROVIDER = "PROVIDER";

    private static final String SOURCE_ESTIMATED = "ESTIMATED";

    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ReactiveExtensionClient client;

    private final LangChain4jChat langChain4jChat;

    private final RouteResolution routeResolution;

    private final AiSettings aiSettings;

    private final GatewaySessions gatewaySessions;

    private final Conversations conversations;

    private final AiAgentAdapterRegistry agentAdapterRegistry;

    private final AiMemoryContextBuilder memoryContextBuilder;

    private final PlanResolution planResolution;

    private final PlanQuotas planQuotas;

    private final AiChatRequestRepository chatRequestRepository;

    private final MemoryTaskScheduler memoryTaskScheduler;

    private final CircuitBreaker circuitBreaker;

    private final AiGatewayRateLimiter rateLimiter;

    private final GatewayKeys gatewayKeys;

    private final SunkRouteRegistry sunkRouteRegistry;

    private final AiProperties properties;

    private final AiMetrics metrics;

    private final ProviderSpendGuard providerSpendGuard;

    private final TeamQuotas teamQuotas;

    public Mono<AiGatewayResult> complete(AiGatewayInvocation invocation) {
        return prepare(invocation)
                .flatMap(prepared -> gatewayKeys
                        .acquire(prepared.access())
                        .onErrorResume(error -> fail(prepared, error).then(Mono.error(upstreamError(error))))
                        .flatMap(permit -> Mono.usingWhen(
                                Mono.just(permit),
                                ignored -> chat(prepared, prepared.candidates(), 0, 0, null)
                                        .onErrorResume(error ->
                                                fail(prepared, error).then(Mono.error(upstreamError(error))))
                                        .flatMap(attempt -> succeed(prepared, attempt)),
                                gatewayKeys::release)));
    }

    public Flux<AiGatewayEvent> stream(AiGatewayInvocation invocation) {
        return Flux.defer(() -> prepare(invocation).flatMapMany(prepared -> {
            var meta = new AiGatewayMeta(
                    invocation.requestId(),
                    prepared.conversation().id(),
                    prepared.resolution().chatMode() == null
                            ? null
                            : prepared.resolution().chatMode().getMetadata().getName(),
                    prepared.resolution().routeGroup().getMetadata().getName(),
                    prepared.selection().provider(),
                    prepared.selection().account(),
                    prepared.resources().model().getSpec().getModelCode(),
                    prepared.selection().tier(),
                    prepared.session().teamId(),
                    prepared.session().agentId(),
                    prepared.session().sessionKey());
            var emitted = new AtomicBoolean();
            var finished = new AtomicBoolean();
            var usage = new AtomicReference<AiTokenUsage>();
            var finishReason = new AtomicReference<String>();
            var actual = new AtomicReference<Resources>();
            var content = new StringBuilder();
            var deltas = streamChat(
                            prepared,
                            prepared.candidates(),
                            0,
                            0,
                            emitted,
                            actual)
                    .doOnNext(event -> {
                        if (event.content() != null) {
                            content.append(event.content());
                        }
                        if (event.usage() != null) {
                            usage.set(event.usage());
                        }
                        if (event.finishReason() != null) {
                            finishReason.set(event.finishReason());
                        }
                    })
                    .map(Delta::new);
            var tail = Flux.defer(() -> streamSucceed(
                            prepared,
                            actual.get(),
                            usage.get(),
                            finishReason.get(),
                            content.toString())
                    .doOnNext(event -> finished.set(true)));
            var events = Flux.concat(Mono.just(new Meta(meta)), deltas, tail)
                    .onErrorResume(error -> finished.get()
                            ? Flux.empty()
                            : streamFail(prepared, actual.get(), content.toString(), error)
                                    .doOnNext(event -> finished.set(true)))
                    .doOnCancel(() -> {
                        if (!finished.get()) {
                            streamCancel(prepared, content.toString()).subscribe();
                        }
                    });
            return gatewayKeys
                    .acquire(prepared.access())
                    .flatMapMany(permit -> Flux.usingWhen(
                            Mono.just(permit), ignored -> events, gatewayKeys::release))
                    .onErrorResume(error ->
                            streamFail(prepared, actual.get(), content.toString(), error));
        }));
    }

    private Mono<Prepared> prepare(AiGatewayInvocation invocation) {
        if (!properties.getGateway().isEnabled()) {
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "ai-disabled", "AI 功能未启用。"));
        }
        return aiSettings.spec().flatMap(setting -> prepare(invocation, setting));
    }

    private Mono<Prepared> prepare(AiGatewayInvocation invocation, AiSetting.Spec setting) {
        return bind(invocation, null).flatMap(session -> inject(invocation, setting, session)
                .flatMap(injected -> plan(injected).flatMap(plan -> prepare(injected, setting, session, plan))));
    }

    // SYSTEM 调用不解析套餐，也不占用用户窗口和团队配额
    private Mono<PlanContext> plan(AiGatewayInvocation invocation) {
        if (invocation.usageScope() == AiPlan.BillingScope.SYSTEM) {
            return Mono.just(PlanContext.SYSTEM);
        }
        return planResolution.resolve(invocation.userId()).map(PlanContext::new);
    }

    private Mono<Prepared> prepare(
            AiGatewayInvocation invocation,
            AiSetting.Spec setting,
            GatewaySession session,
            PlanContext plan) {
        return route(invocation, setting, invocation.modeCode(), invocation.routeGroupCode())
                .flatMap(resolution -> attempt(invocation, setting, session, resolution, plan))
                // 5 小时窗口耗尽时允许按套餐配置降级一次，周窗口耗尽保持拒绝
                .onErrorResume(error -> canDowngrade(plan, error)
                        ? route(invocation, setting, null, downgradeGroup(plan))
                                .flatMap(resolution -> attempt(invocation, setting, session, resolution, plan))
                        : Mono.error(error));
    }

    private Mono<RouteResolution.Resolution> route(
            AiGatewayInvocation invocation,
            AiSetting.Spec setting,
            @Nullable String modeCode,
            @Nullable String routeGroupCode) {
        return routeResolution
                .resolve(new RouteResolution.Input(
                        modeCode,
                        routeGroupCode,
                        null,
                        invocation.ir(),
                        setting,
                        null,
                        invocation.modelOverride(),
                        invocation.agentName()))
                .doOnNext(this::recordRoutingDecision)
                .doOnError(error -> recordRouteExhausted(invocation, error));
    }

    private static boolean canDowngrade(PlanContext plan, Throwable error) {
        if (!(error instanceof PlanQuotaExceededException quota)
                || quota.windowKind() != PlanWindowKind.FIVE_HOUR) {
            return false;
        }
        var config = plan.plan();
        return config != null
                && config.getSpec() != null
                && config.getSpec().getFallback() != null
                && config.getSpec().getFallback().downgrades();
    }

    private static @Nullable String downgradeGroup(PlanContext plan) {
        var config = plan.plan();
        return config == null || config.getSpec() == null || config.getSpec().getFallback() == null
                ? null
                : config.getSpec().getFallback().getDowngradeRouteGroup();
    }

    private Mono<Prepared> attempt(
            AiGatewayInvocation invocation,
            AiSetting.Spec setting,
            GatewaySession session,
            RouteResolution.Resolution resolution,
            PlanContext plan) {
        return requireAccess(invocation, resolution, plan)
                .then(load(resolution.selection()))
                .flatMap(resources -> gatewayKeys.authorize(invocation, resources.model())
                        .flatMap(access -> {
                            var candidates = candidates(resolution, access, plan);
                            if (candidates.isEmpty()) {
                                return Mono.error(planDenied(
                                        "当前套餐不允许使用模型 " + resolution.selection().model() + "。"));
                            }
                            return hold(invocation, setting, session, resolution, resources, plan)
                                    .flatMap(hold -> conversation(invocation, resolution, session)
                                            .flatMap(conversation -> bind(invocation, conversation.id())
                                                    .flatMap(bound -> createChatRequest(
                                                                    invocation, resolution, bound, conversation, plan, hold)
                                                            .onErrorResume(error -> release(hold)
                                                                    .then(Mono.error(error)))
                                                            .thenReturn(new Prepared(
                                                                    invocation,
                                                                    setting,
                                                                    bound,
                                                                    resolution,
                                                                    resources,
                                                                    conversation,
                                                                    plan,
                                                                    hold,
                                                                    Instant.now(),
                                                                    access,
                                                                    candidates,
                                                                    new ArrayList<>())))));
                        }));
    }

    private static Mono<Void> requireAccess(
            AiGatewayInvocation invocation,
            RouteResolution.Resolution resolution,
            PlanContext plan) {
        var config = plan.plan();
        if (config == null || config.getSpec() == null) {
            return Mono.empty();
        }
        var access = config.getSpec().getAccess() == null
                ? new AiPlan.Access()
                : config.getSpec().getAccess();
        if (!access.allowsFeature(featureOf(invocation))) {
            return Mono.error(planDenied("当前套餐不允许使用 " + featureOf(invocation) + " 功能。"));
        }
        var group = resolution.routeGroup() == null || resolution.routeGroup().getMetadata() == null
                ? null
                : resolution.routeGroup().getMetadata().getName();
        if (!access.allowsRouteGroup(group)) {
            return Mono.error(planDenied("当前套餐不允许使用路由组 " + group + "。"));
        }
        return Mono.empty();
    }

    private Mono<AiGatewayInvocation> inject(
            AiGatewayInvocation invocation, AiSetting.Spec setting, GatewaySession session) {
        var gateway = setting.getGateway() == null ? new AiSetting.Gateway() : setting.getGateway();
        if (!properties.getGateway().isInjectionEnabled() || !gateway.isInjectionEnabled()) {
            return Mono.just(invocation);
        }
        var adapter = agentAdapterRegistry.resolve(invocation.agentSource());
        var request = new AiChatRequest(invocation.protocol(), invocation.ir());
        if (adapter.classifyRequest(request) != AiRequestKind.MAIN) {
            return Mono.just(invocation);
        }
        var query = adapter.extractUserText(request).orElse(null);
        if (!StringUtils.hasText(query)) {
            return Mono.just(invocation);
        }
        var retrieval = setting.getRetrieval() == null ? new AiSetting.Retrieval() : setting.getRetrieval();
        return memoryContextBuilder
                .build(
                        new MemoryContext(
                                session.teamId(),
                                invocation.userId(),
                                session.agentId(),
                                invocation.sessionKey(),
                                null),
                        query,
                        Math.max(1, retrieval.getL1Limit()),
                        Math.max(1, retrieval.getL2Limit()))
                .map(injection -> {
                    var system = memoryContextBuilder.systemMessage(injection);
                    return StringUtils.hasText(system) ? withInjectedSystem(invocation, system) : invocation;
                })
                .onErrorResume(error -> {
                    log.warn("记忆注入失败，透传原始请求。", error);
                    return Mono.just(invocation);
                });
    }

    private static AiGatewayInvocation withInjectedSystem(AiGatewayInvocation invocation, String system) {
        var messages = new ArrayList<AiChatMessage>();
        messages.add(new AiChatMessage("system", List.of(new AiContentPart.Text(system, null))));
        messages.addAll(invocation.ir().messages());
        var ir = new AiChatIr(
                messages, invocation.ir().tools(), invocation.ir().requestParams(), invocation.ir().stream());
        return new AiGatewayInvocation(
                invocation.protocol(),
                ir,
                invocation.userId(),
                invocation.agentSource(),
                invocation.sessionKey(),
                invocation.sessionId(),
                invocation.requestId(),
                invocation.idempotencyKey(),
                invocation.modeCode(),
                invocation.routeGroupCode(),
                invocation.agentName(),
                invocation.conversationId(),
                invocation.modelOverride(),
                invocation.callerKey(),
                invocation.usageScope());
    }

    private Mono<GatewaySession> bind(AiGatewayInvocation invocation, @Nullable Long conversationId) {
        return gatewaySessions.bind(new BindRequest(
                invocation.userId(),
                invocation.agentSource(),
                invocation.sessionKey(),
                invocation.sessionId(),
                invocation.agentName(),
                null,
                conversationId));
    }

    private Mono<Conversation> conversation(
            AiGatewayInvocation invocation,
            RouteResolution.Resolution resolution,
            GatewaySession session) {
        if (invocation.conversationId() != null) {
            return conversations.requireOwned(invocation.conversationId(), invocation.userId());
        }
        return conversations.createOrGet(new NewConversation(
                GatewaySessions.DEFAULT_SPACE,
                invocation.userId(),
                session.teamId(),
                session.agentId(),
                invocation.agentSource(),
                invocation.sessionKey(),
                null,
                null,
                resolution.chatMode() == null ? null : resolution.chatMode().getMetadata().getName(),
                resolution.routeGroup().getMetadata().getName()));
    }

    private Mono<Hold> hold(
            AiGatewayInvocation invocation,
            AiSetting.Spec setting,
            GatewaySession session,
            RouteResolution.Resolution resolution,
            Resources resources,
            PlanContext plan) {
        var request = resolution.request();
        var seat = seatOf(resolution, resolution.selection());
        var free = (seat != null && (seat.isFree() || seat.isFreePlan())) || freeProvider(resources);
        var maxOutput = request.getMaxOutputTokens() > 0
                ? request.getMaxOutputTokens()
                : defaultMaxOutput(resources.model());
        var units = new UsageUnitCalculator(billing(setting), free)
                .estimate(
                        request.getPromptTokens(),
                        maxOutput,
                        resolution.selection().tier(),
                        resources.model().getSpec().getModelCode())
                .longValue();
        var amount = BigDecimal.valueOf(units);
        var config = plan.plan();
        var guard = providerSpendGuard
                .check(resources.provider(), resources.account(), amount)
                .then(config == null ? Mono.empty() : teamQuotas.check(session.teamId(), amount));
        return guard.then(Mono.defer(() -> {
            if (config == null || free) {
                return Mono.just(new Hold(null, free));
            }
            return planQuotas
                    .reserve(
                            invocation.requestId(),
                            invocation.userId(),
                            config,
                            units,
                            featureOf(invocation).name(),
                            session.teamId(),
                            invocation.requestId())
                    .map(reservation -> new Hold(reservation, false));
        }));
    }

    private static AiSetting.Billing billing(AiSetting.@Nullable Spec setting) {
        return setting == null || setting.getBilling() == null
                ? new AiSetting.Billing()
                : setting.getBilling();
    }

    private Mono<Void> createChatRequest(
            AiGatewayInvocation invocation,
            RouteResolution.Resolution resolution,
            GatewaySession session,
            Conversation conversation,
            PlanContext plan,
            Hold hold) {
        var selection = resolution.selection();
        return chatRequestRepository
                .createChatRequest(new NewChatRequest(
                        invocation.requestId(),
                        GatewaySessions.DEFAULT_SPACE,
                        invocation.userId(),
                        invocation.agentSource(),
                        session.teamId(),
                        session.agentId(),
                        invocation.sessionKey(),
                        conversation.id(),
                        invocation.idempotencyKey(),
                        resolution.chatMode() == null ? null : resolution.chatMode().getMetadata().getName(),
                        resolution.routeGroup().getMetadata().getName(),
                        selection.provider(),
                        selection.account(),
                        selection.model(),
                        selection.tier() == null ? null : selection.tier().name(),
                        json(gatewayTrace(resolution.plan().trace(), invocation, plan)),
                        invocation.callerKey(),
                        // 仅实际预留额度时写入套餐名，结算与用量归属据此判断是否存在预留
                        hold.reservation() == null ? null : hold.reservation().planId(),
                        invocation.usageScope().name()))
                .then();
    }

    private Mono<Attempted> chat(
            Prepared prepared,
            List<Candidate> candidates,
            int index,
            int retriesUsed,
            @Nullable Throwable lastError) {
        if (index >= candidates.size()) {
            metrics.providerExhausted(prepared.resolution().routeGroup().getMetadata().getName());
            return Mono.error(lastError == null
                    ? upstreamError(new IllegalStateException("没有可用候选"))
                    : lastError);
        }
        var candidate = candidates.get(index);
        var selection = candidate.selection();
        var startedAt = Instant.now();
        return load(selection)
                .flatMap(resources -> chatOnce(prepared, resources))
                .doOnSuccess(attempt -> recordAttempt(
                        prepared, candidate, true, HttpStatus.OK.value(), null, startedAt))
                .onErrorResume(error -> {
                    recordAttempt(prepared, candidate, false, statusOf(error), errorCodeOf(error), startedAt);
                    recordAttemptFailure(selection, error);
                    var policy = retryPolicy(prepared);
                    if (policy.canRetry(retriesUsed) && policy.retryable(error)) {
                        return Mono.delay(policy.delay(retriesUsed))
                                .then(chat(prepared, candidates, index, retriesUsed + 1, error));
                    }
                    return chat(prepared, candidates, index + 1, 0, error);
                });
    }

    private Mono<Attempted> chatOnce(Prepared prepared, Resources resources) {
        var call = langChain4jChat
                .chat(
                        resources.provider(),
                        resources.account(),
                        resources.model(),
                        protocolOf(resources),
                        prepared.invocation().ir())
                .timeout(attemptTimeout(prepared))
                .doOnSuccess(response -> recordSuccess(resources))
                .map(response -> new Attempted(response, resources));
        return withRateLimit(prepared, resources, call);
    }

    private Flux<AiStreamEvent> streamChat(
            Prepared prepared,
            List<Candidate> candidates,
            int index,
            int retriesUsed,
            AtomicBoolean emitted,
            AtomicReference<Resources> actual) {
        if (index >= candidates.size()) {
            metrics.providerExhausted(prepared.resolution().routeGroup().getMetadata().getName());
            return Flux.error(upstreamError(new IllegalStateException("没有可用候选")));
        }
        var candidate = candidates.get(index);
        var selection = candidate.selection();
        var startedAt = Instant.now();
        return load(selection)
                .flatMapMany(resources -> {
                    actual.set(resources);
                    Flux<AiStreamEvent> stream = langChain4jChat
                            .streamChat(
                                    resources.provider(),
                                    resources.account(),
                                    resources.model(),
                                    protocolOf(resources),
                                    prepared.invocation().ir())
                            .doOnNext(event -> emitted.set(true))
                            .doOnComplete(() -> {
                                recordAttempt(prepared, candidate, true, HttpStatus.OK.value(), null, startedAt);
                                recordSuccess(resources);
                            });
                    var firstToken = firstTokenTimeout(prepared);
                    if (firstToken != null) {
                        stream = stream.timeout(Mono.delay(firstToken), event -> Mono.never());
                    }
                    return withRateLimit(prepared, resources, stream);
                })
                .onErrorResume(error -> {
                    recordAttempt(prepared, candidate, false, statusOf(error), errorCodeOf(error), startedAt);
                    recordAttemptFailure(selection, error);
                    if (emitted.get()) {
                        return Flux.error(error);
                    }
                    var policy = retryPolicy(prepared);
                    if (policy.canRetry(retriesUsed) && policy.retryable(error)) {
                        return streamChat(prepared, candidates, index, retriesUsed + 1, emitted, actual)
                                .delaySubscription(policy.delay(retriesUsed));
                    }
                    return streamChat(prepared, candidates, index + 1, 0, emitted, actual);
                });
    }

    private <T> Mono<T> withRateLimit(Prepared prepared, Resources resources, Mono<T> call) {
        if (!rateLimitEnabled(prepared.setting())) {
            return call;
        }
        var userLimit = concurrentLimit(prepared);
        var userRpm = rpmLimit(prepared);
        return Mono.usingWhen(
                rateLimiter.acquire(resources.provider(), resources.account()),
                permit -> userLimit > 0 || userRpm > 0
                        ? Mono.usingWhen(
                                rateLimiter.acquireUser(
                                        prepared.invocation().userId(), userLimit, userRpm),
                                userPermit -> call,
                                rateLimiter::release)
                        : call,
                rateLimiter::release);
    }

    private Flux<AiStreamEvent> withRateLimit(
            Prepared prepared, Resources resources, Flux<AiStreamEvent> stream) {
        if (!rateLimitEnabled(prepared.setting())) {
            return stream;
        }
        var userLimit = concurrentLimit(prepared);
        var userRpm = rpmLimit(prepared);
        return Flux.usingWhen(
                rateLimiter.acquire(resources.provider(), resources.account()),
                permit -> userLimit > 0 || userRpm > 0
                        ? Flux.usingWhen(
                                rateLimiter.acquireUser(
                                        prepared.invocation().userId(), userLimit, userRpm),
                                userPermit -> stream,
                                rateLimiter::release)
                        : stream,
                rateLimiter::release);
    }

    // 用户并发与速率只取套餐配置，未分配套餐的请求在解析阶段已拒绝
    private static int concurrentLimit(Prepared prepared) {
        var limits = rateLimits(prepared);
        return limits == null ? 0 : Math.max(0, limits.getMaxConcurrency());
    }

    private static int rpmLimit(Prepared prepared) {
        var limits = rateLimits(prepared);
        return limits == null ? 0 : Math.max(0, limits.getMaxRpm());
    }

    private static AiPlan.@Nullable RateLimit rateLimits(Prepared prepared) {
        var config = prepared.plan().plan();
        return config == null || config.getSpec() == null ? null : config.getSpec().getRateLimit();
    }

    private static Duration attemptTimeout(Prepared prepared) {
        var gateway = gatewayOf(prepared.setting());
        var requestTimeout = Math.max(1, gateway.getRequestTimeoutSeconds());
        var patience = prepared.resolution().request().getPatienceSeconds();
        var seconds = patience > 0 ? Math.min(patience, requestTimeout) : requestTimeout;
        return Duration.ofSeconds(seconds);
    }

    private static @Nullable Duration firstTokenTimeout(Prepared prepared) {
        var request = prepared.resolution().request();
        var seconds = request.getFirstTokenSeconds() > 0
                ? request.getFirstTokenSeconds()
                : request.getPatienceSeconds();
        return seconds > 0 ? Duration.ofSeconds(seconds) : null;
    }

    private static AiSetting.Gateway gatewayOf(AiSetting.Spec setting) {
        return setting == null || setting.getGateway() == null ? new AiSetting.Gateway() : setting.getGateway();
    }

    private static boolean rateLimitEnabled(AiSetting.Spec setting) {
        return setting == null || setting.getGateway() == null || setting.getGateway().isRateLimitEnabled();
    }

    private static AiGatewayRetryPolicy retryPolicy(Prepared prepared) {
        return new AiGatewayRetryPolicy(prepared.setting().getGateway());
    }

    private Mono<AiGatewayResult> succeed(Prepared prepared, Attempted attempt) {
        var usage = resolveUsage(prepared, attempt.response().usage(), textOf(attempt.response().message()));
        var units = charge(prepared, attempt.resources(), usage);
        var billingSource = billingSource(attempt.response().usage());
        var settle = settle(prepared, attempt.resources(), usage);
        var latencyDuration = Duration.between(prepared.startedAt(), Instant.now());
        var latency = (int) latencyDuration.toMillis();
        recordSuccessMetrics(prepared, attempt.resources(), usage, units, latencyDuration);
        return settle.then(complete(prepared, usage, units, billingSource, STATUS_SUCCEEDED, null,
                        BILLING_SETTLED, attempt.response().id(), latency, latency))
                .then(captureExternal(
                        prepared,
                        textOf(attempt.response().message()),
                        attempt.resources(),
                        usage))
                .thenReturn(new AiGatewayResult(
                        attempt.response(),
                        meta(prepared, attempt.resources()),
                        usage,
                        units,
                        billingSource));
    }

    private Mono<Void> captureExternal(
            Prepared prepared, @Nullable String assistantText, Resources resources, AiTokenUsage usage) {
        var invocation = prepared.invocation();
        if (AGENT_SOURCE_CHAT.equals(invocation.agentSource()) || assistantText == null) {
            return Mono.empty();
        }
        var adapter = agentAdapterRegistry.resolve(invocation.agentSource());
        if (adapter.classifyRequest(new AiChatRequest(invocation.protocol(), invocation.ir()))
                != AiRequestKind.MAIN) {
            return Mono.empty();
        }
        var userText = lastUserText(invocation.ir());
        if (!StringUtils.hasText(userText)) {
            return Mono.empty();
        }
        var memory = prepared.setting().getMemory() == null
                ? new AiSetting.Memory()
                : prepared.setting().getMemory();
        return conversations
                .recentHistory(prepared.conversation(), 200)
                .collectList()
                .flatMap(history -> {
                    var turn = history.isEmpty() ? 1 : history.getLast().turnIndex() + 1;
                    return conversations
                            .appendUserMessage(
                                    prepared.conversation(), turn, 0, userText, invocation.requestId())
                            .flatMap(userMessage -> conversations
                                    .appendAssistantMessage(
                                            prepared.conversation(),
                                            turn,
                                            1,
                                            assistantText,
                                            invocation.requestId(),
                                            resources.model().getSpec().getModelCode(),
                                            usage.outputTokens())
                                    .flatMap(assistantMessage -> conversations
                                            .markPending(prepared.conversation(), 2)
                                            .then(memory.isExtractionEnabled()
                                                    ? memoryTaskScheduler.enqueueExtraction(
                                                            prepared.conversation(),
                                                            invocation.userId(),
                                                            assistantMessage.id())
                                                    : Mono.empty())));
                })
                .onErrorResume(error -> {
                    log.warn("Agent 网关 L0 归档失败。", error);
                    return Mono.empty();
                })
                .then();
    }


    private static String lastUserText(AiChatIr ir) {
        var messages = ir.messages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if (!"user".equals(message.role())) {
                continue;
            }
            var text = message.parts().stream()
                    .filter(AiContentPart.Text.class::isInstance)
                    .map(AiContentPart.Text.class::cast)
                    .map(AiContentPart.Text::text)
                    .filter(value -> !value.isBlank())
                    .reduce((left, right) -> right);
            if (text.isPresent()) {
                return text.get();
            }
        }
        return null;
    }

    private Mono<Void> fail(Prepared prepared, Throwable error) {
        var release = release(prepared.hold());
        var latencyDuration = Duration.between(prepared.startedAt(), Instant.now());
        var latency = (int) latencyDuration.toMillis();
        recordFailureMetrics(prepared, latencyDuration);
        var mapped = upstreamError(error);
        return release.then(complete(
                prepared, null, null, null, STATUS_FAILED, codeOf(mapped), BILLING_RELEASED, null, latency, null))
                .then();
    }

    private Flux<AiGatewayEvent> streamSucceed(
            Prepared prepared,
            @Nullable Resources actual,
            @Nullable AiTokenUsage reported,
            @Nullable String finishReason,
            String content) {
        var resources = actual == null ? prepared.resources() : actual;
        var usage = resolveUsage(prepared, reported, content);
        var units = charge(prepared, resources, usage);
        var billingSource = billingSource(reported);
        var settle = settle(prepared, resources, usage);
        var latencyDuration = Duration.between(prepared.startedAt(), Instant.now());
        var latency = (int) latencyDuration.toMillis();
        recordSuccessMetrics(prepared, resources, usage, units, latencyDuration);
        return settle.thenMany(complete(
                        prepared, usage, units, billingSource, STATUS_SUCCEEDED, null, BILLING_SETTLED,
                        null, latency, null)
                .thenMany(captureExternal(prepared, content, resources, usage))
                .thenMany(Flux.just(
                        new Usage(usage, units, billingSource),
                        new Done("msg-" + prepared.invocation().requestId(), finishReason, false))));
    }

    private Flux<AiGatewayEvent> streamFail(
            Prepared prepared, @Nullable Resources actual, String content, Throwable error) {
        var resources = actual == null ? prepared.resources() : actual;
        var partial = !content.isEmpty();
        var usage = resolveUsage(prepared, null, content);
        var units = partial ? charge(prepared, resources, usage) : ZERO;
        var billingStatus = partial ? BILLING_SETTLED : BILLING_RELEASED;
        var billingSource = partial ? SOURCE_ESTIMATED : null;
        var action = partial ? settle(prepared, resources, usage) : release(prepared.hold());
        var mapped = upstreamError(error);
        var latencyDuration = Duration.between(prepared.startedAt(), Instant.now());
        var latency = (int) latencyDuration.toMillis();
        recordFailureMetrics(prepared, latencyDuration);
        return action.thenMany(complete(
                        prepared, usage, units, billingSource, STATUS_FAILED, codeOf(mapped),
                        billingStatus, null, latency, null)
                .thenMany(Flux.just(new Error(
                        codeOf(mapped), reasonOf(mapped), mapped.getStatusCode().is5xxServerError(), partial))));
    }

    private Mono<Void> streamCancel(Prepared prepared, String content) {
        if (content.isEmpty()) {
            return release(prepared.hold());
        }
        var usage = resolveUsage(prepared, null, content);
        return settle(prepared, prepared.resources(), usage);
    }

    private Mono<Void> complete(
            Prepared prepared,
            @Nullable AiTokenUsage usage,
            @Nullable BigDecimal units,
            @Nullable String billingSource,
            String status,
            @Nullable String errorCode,
            String billingStatus,
            @Nullable String upstreamRequestId,
            int latencyMillis,
            @Nullable Integer ttftMillis) {
        var output = usage == null || usage.outputTokens() == null ? null : usage.outputTokens();
        var total = usage == null
                ? null
                : nullSafe(usage.inputTokens()) + nullSafe(usage.outputTokens());
        var tps = output == null || latencyMillis <= 0
                ? null
                : BigDecimal.valueOf(output * 1000L)
                        .divide(BigDecimal.valueOf(latencyMillis), 2, RoundingMode.HALF_UP);
        return chatRequestRepository
                .completeChatRequest(new ChatCompletion(
                        prepared.invocation().requestId(),
                        status,
                        errorCode,
                        usage == null ? null : usage.inputTokens(),
                        cacheTokens(usage),
                        output,
                        total,
                        units == null ? null : units.longValue(),
                        billingSource,
                        billingStatus,
                        latencyMillis,
                        ttftMillis,
                        tps,
                        billingSource,
                        upstreamRequestId,
                        finalTrace(prepared),
                        json(List.copyOf(prepared.attempts()))))
                .then();
    }

    private static String finalTrace(Prepared prepared) {
        var trace = prepared.resolution().plan().trace()
                .withAttempts(List.copyOf(prepared.attempts()));
        return json(gatewayTrace(trace, prepared.invocation(), prepared.plan()));
    }

    // 用途按调用来源区分，入口按网关协议固定，便于用量页按 purpose/via 筛选。
    private static JsonNode gatewayTrace(
            RouteTrace trace, AiGatewayInvocation invocation, PlanContext plan) {
        var node = JsonUtils.mapper()
                .valueToTree(trace.withCallerKey(invocation.callerKey())
                        .withGatewayContext(purpose(invocation), via(invocation)));
        if (node instanceof ObjectNode object) {
            object.put("tries", trace.attempts().size());
            object.put("billingScope", invocation.usageScope().name());
            if (plan.resolved() != null && plan.resolved().planName() != null) {
                object.put("planId", plan.resolved().planName());
            }
            if (trace.selected() != null && trace.selected().tier() != null) {
                object.put("servedEffort", trace.selected().tier().name());
            }
        }
        return node;
    }

    private static String purpose(AiGatewayInvocation invocation) {
        var source = invocation.agentSource();
        if (AGENT_SOURCE_CHAT.equals(source)) {
            return "chat";
        }
        if (source != null && source.toLowerCase(Locale.ROOT).contains("generation")) {
            return "generation";
        }
        return "gateway";
    }

    private static String via(AiGatewayInvocation invocation) {
        return invocation.protocol().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static @Nullable String json(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JsonUtils.objectToJson(value);
        } catch (RuntimeException error) {
            log.warn("AI 网关轨迹序列化失败。", error);
            return null;
        }
    }

    private Mono<Resources> load(RouteSelection selection) {
        return client.get(AiProvider.class, selection.provider())
                .flatMap(provider -> client.get(AiProviderAccount.class, selection.account())
                        .flatMap(account -> client.get(AiModel.class, selection.model())
                                .map(model -> new Resources(provider, account, model))));
    }

    private void recordSuccess(Resources resources) {
        if (resources.provider() != null) {
            circuitBreaker.recordSuccess(resources.provider().getMetadata().getName());
        }
        if (resources.account() != null) {
            circuitBreaker.recordSuccess(resources.account().getMetadata().getName());
        }
    }

    private void recordAttemptFailure(RouteSelection selection, Throwable error) {
        if (error instanceof ExtensionNotFoundException) {
            return;
        }
        var status = AiGatewayRetryPolicy.statusOf(error);
        if (status != null && status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            sunkRouteRegistry.markSunk(selection.provider(), selection.account());
        }
        if (error instanceof ApiException api && AiGatewayRateLimiter.CODE_RATE_LIMITED.equals(api.getCode())) {
            // 本地限流不是供应商故障，不触发熔断
            return;
        }
        log.warn("AI 供应商调用失败：{}/{}", selection.provider(), selection.account(), error);
        circuitBreaker.recordFailure(selection.provider(), CircuitFailure.defaultFailure());
        circuitBreaker.recordFailure(selection.account(), CircuitFailure.defaultFailure());
    }

    private void recordSuccessMetrics(
            Prepared prepared, Resources resources, AiTokenUsage usage, BigDecimal units, Duration latency) {
        var protocol = protocolOf(prepared.invocation());
        var provider = resources.provider().getMetadata().getName();
        var model = resources.model().getSpec().getModelCode();
        metrics.gatewayRequest(protocol, prepared.invocation().agentSource(), AiMetrics.STATUS_SUCCESS);
        metrics.gatewayLatency(protocol, AiMetrics.STATUS_SUCCESS, latency);
        metrics.gatewayTokens(provider, model, usage.inputTokens(), usage.outputTokens(), cacheTokens(usage));
        metrics.gatewayUnits(units, provider, model);
    }

    private void recordFailureMetrics(Prepared prepared, Duration latency) {
        var protocol = protocolOf(prepared.invocation());
        metrics.gatewayRequest(protocol, prepared.invocation().agentSource(), AiMetrics.STATUS_FAILED);
        metrics.gatewayLatency(protocol, AiMetrics.STATUS_FAILED, latency);
    }

    private void recordRoutingDecision(RouteResolution.Resolution resolution) {
        var trace = resolution.plan().trace();
        metrics.routingDecision(
                resolution.routeGroup().getMetadata().getName(),
                trace.routeMode() == null ? null : trace.routeMode().name(),
                trace.outcome() == null ? null : trace.outcome().name());
    }

    private void recordRouteExhausted(AiGatewayInvocation invocation, Throwable error) {
        if (error instanceof ApiException apiException
                && apiException.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            metrics.routeExhausted(invocation.routeGroupCode());
        }
    }

    private static String protocolOf(AiGatewayInvocation invocation) {
        return invocation.protocol() == null ? null : invocation.protocol().name();
    }

    // 主组候选排在组间 fallback 之前，去重后保持低优先
    private static List<Candidate> candidates(
            RouteResolution.Resolution resolution,
            GatewayKeys.Access access,
            PlanContext plan) {
        var candidates = new ArrayList<Candidate>();
        var seen = new LinkedHashSet<String>();
        var current = resolution;
        while (current != null) {
            var group = current.routeGroup() == null || current.routeGroup().getMetadata() == null
                    ? null
                    : current.routeGroup().getMetadata().getName();
            addCandidate(candidates, seen, current, group, current.plan().selected(), access, plan);
            for (var fallback : current.plan().fallbackSeats()) {
                addCandidate(candidates, seen, current, group, fallback, access, plan);
            }
            current = current.fallbackGroup();
        }
        return List.copyOf(candidates);
    }

    private static void addCandidate(
            List<Candidate> candidates,
            LinkedHashSet<String> seen,
            RouteResolution.Resolution resolution,
            @Nullable String group,
            @Nullable RouteSelection selection,
            GatewayKeys.Access access,
            PlanContext plan) {
        if (selection == null || !access.allows(selection.model()) || !planAllows(plan, group, selection)) {
            return;
        }
        var key = selection.provider() + "|" + selection.account() + "|" + selection.model();
        if (!seen.add(key)) {
            return;
        }
        var seat = seatOf(resolution, selection);
        candidates.add(new Candidate(selection, group, seat == null ? null : seat.getFallbackSource()));
    }

    private static boolean planAllows(PlanContext plan, @Nullable String group, RouteSelection selection) {
        var config = plan.plan();
        if (config == null || config.getSpec() == null) {
            return true;
        }
        var access = config.getSpec().getAccess() == null
                ? new AiPlan.Access()
                : config.getSpec().getAccess();
        return access.allowsRouteGroup(group) && access.allowsModel(selection.model());
    }

    private static void recordAttempt(
            Prepared prepared,
            Candidate candidate,
            boolean success,
            @Nullable Integer status,
            @Nullable String errorCode,
            Instant startedAt) {
        var selection = candidate.selection();
        var latency = Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
        var attempts = prepared.attempts();
        synchronized (attempts) {
            attempts.add(new RouteTrace.Attempt(
                    candidate.group(),
                    selection.provider(),
                    selection.account(),
                    selection.model(),
                    selection.tier(),
                    candidate.source(),
                    attempts.size() + 1,
                    success,
                    status,
                    errorCode,
                    latency,
                    Instant.now()));
        }
    }

    private static @Nullable Integer statusOf(Throwable error) {
        if (error instanceof ApiException api) {
            return api.getStatusCode().value();
        }
        return AiGatewayRetryPolicy.statusOf(error);
    }

    private static @Nullable String errorCodeOf(Throwable error) {
        return error instanceof ApiException api ? api.getCode() : null;
    }

    private static AiProvider.Protocol protocolOf(Resources resources) {
        var account = resources.account() == null ? null : resources.account().getSpec();
        var override = account == null ? null : account.getProtocolOverride();
        return override == null ? resources.provider().getSpec().getProtocol() : override;
    }

    private static RouteSeat seatOf(RouteResolution.Resolution resolution, RouteSelection selection) {
        return resolution.seats().stream()
                .filter(seat -> Objects.equals(seat.getProvider(), selection.provider())
                        && Objects.equals(seat.getAccount(), selection.account())
                        && Objects.equals(seat.getModel(), selection.model()))
                .findFirst()
                .orElse(null);
    }

    private static int defaultMaxOutput(AiModel model) {
        var value = model.getSpec() == null ? null : model.getSpec().getMaxOutputTokens();
        return value == null || value <= 0 ? DEFAULT_MAX_OUTPUT_TOKENS : value;
    }

    private AiTokenUsage resolveUsage(Prepared prepared, @Nullable AiTokenUsage reported, String outputText) {
        var prompt = prepared.resolution().request().getPromptTokens();
        var input = reported != null && reported.inputTokens() != null ? reported.inputTokens() : prompt;
        var output = reported != null && reported.outputTokens() != null
                ? reported.outputTokens()
                : AiTokenEstimator.estimateText(outputText);
        var cacheRead = reported == null || reported.cacheReadTokens() == null ? 0 : reported.cacheReadTokens();
        var cacheWrite = reported == null || reported.cacheWriteTokens() == null ? 0 : reported.cacheWriteTokens();
        return new AiTokenUsage(input, output, cacheRead, cacheWrite);
    }

    private static BigDecimal charge(Prepared prepared, Resources resources, AiTokenUsage usage) {
        var calculator = new UsageUnitCalculator(
                billing(prepared.setting()), prepared.hold().free() || freeProvider(resources));
        var modelUsage = new AiModelUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheReadTokens(),
                usage.cacheWriteTokens(),
                null,
                null,
                null,
                null,
                null,
                Map.of());
        return calculator.charge(modelUsage, prepared.selection().tier(), resources.model().getSpec().getModelCode());
    }

    private Mono<Void> settle(Prepared prepared, Resources resources, AiTokenUsage usage) {
        var reservation = prepared.hold().reservation();
        if (reservation == null) {
            return Mono.empty();
        }
        var snapshot = new PlanUsageSnapshot(
                resources.provider().getMetadata().getName(),
                resources.account().getMetadata().getName(),
                resources.model().getSpec().getModelCode(),
                prepared.selection().tier(),
                usage.inputTokens(),
                cacheTokens(usage),
                usage.outputTokens(),
                prepared.invocation().requestId());
        return planQuotas
                .settle(prepared.invocation().requestId(), charge(prepared, resources, usage).longValue(), snapshot)
                .then();
    }

    private static boolean freeProvider(Resources resources) {
        return (resources.provider().getSpec() != null && resources.provider().getSpec().isFree())
                || (resources.account().getSpec() != null && resources.account().getSpec().isFreePlan());
    }

    private static AiPlan.Feature featureOf(AiGatewayInvocation invocation) {
        var source = invocation.agentSource();
        if (AGENT_SOURCE_CHAT.equals(source)) {
            return AiPlan.Feature.CHAT;
        }
        if (source != null) {
            var normalized = source.toLowerCase(Locale.ROOT);
            if (normalized.contains("subagent")) {
                return AiPlan.Feature.SUBAGENT;
            }
            if (normalized.contains("knowledge")) {
                return AiPlan.Feature.KNOWLEDGE;
            }
        }
        return AiPlan.Feature.AGENT;
    }

    private static ApiException planDenied(String reason) {
        return new ApiException(HttpStatus.FORBIDDEN, AiPlan.CODE_MODEL_DENIED, reason);
    }

    private static String billingSource(@Nullable AiTokenUsage usage) {
        return usage != null
                        && (usage.inputTokens() != null
                                || usage.outputTokens() != null
                                || usage.cacheReadTokens() != null
                                || usage.cacheWriteTokens() != null)
                ? SOURCE_PROVIDER
                : SOURCE_ESTIMATED;
    }

    private AiGatewayMeta meta(Prepared prepared, Resources resources) {
        return new AiGatewayMeta(
                prepared.invocation().requestId(),
                prepared.conversation().id(),
                prepared.resolution().chatMode() == null
                        ? null
                        : prepared.resolution().chatMode().getMetadata().getName(),
                prepared.resolution().routeGroup().getMetadata().getName(),
                resources.provider().getMetadata().getName(),
                resources.account().getMetadata().getName(),
                resources.model().getSpec().getModelCode(),
                prepared.selection().tier(),
                prepared.session().teamId(),
                prepared.session().agentId(),
                prepared.session().sessionKey());
    }

    private static String textOf(AiChatMessage message) {
        if (message == null) {
            return "";
        }
        return message.parts().stream()
                .filter(AiContentPart.Text.class::isInstance)
                .map(AiContentPart.Text.class::cast)
                .map(AiContentPart.Text::text)
                .collect(Collectors.joining());
    }

    private static Integer cacheTokens(@Nullable AiTokenUsage usage) {
        return usage == null
                ? null
                : nullSafe(usage.cacheReadTokens()) + nullSafe(usage.cacheWriteTokens());
    }

    private static int nullSafe(@Nullable Integer value) {
        return value == null ? 0 : value;
    }

    private Mono<Void> release(Hold hold) {
        return hold.reservation() == null
                ? Mono.empty()
                : planQuotas.release(hold.reservation().requestId()).then();
    }

    private static ApiException upstreamError(Throwable error) {
        if (error instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "ai-gateway-upstream-error", "上游模型调用失败。", error);
    }

    private static String codeOf(ApiException error) {
        return error.getCode();
    }

    private static String reasonOf(ApiException error) {
        return error.getReason() == null ? "上游模型调用失败。" : error.getReason();
    }

    private record Resources(AiProvider provider, AiProviderAccount account, AiModel model) {}

    private record Hold(PlanQuotas.@Nullable ReserveResult reservation, boolean free) {}

    private record Attempted(AiChatResponse response, Resources resources) {}

    private record Candidate(RouteSelection selection, @Nullable String group, @Nullable String source) {}

    private record PlanContext(PlanResolution.@Nullable ResolvedPlan resolved) {

        private static final PlanContext SYSTEM = new PlanContext(null);

        private @Nullable AiPlan plan() {
            return resolved == null ? null : resolved.plan();
        }
    }

    private record Prepared(
            AiGatewayInvocation invocation,
            AiSetting.Spec setting,
            GatewaySession session,
            RouteResolution.Resolution resolution,
            Resources resources,
            Conversation conversation,
            PlanContext plan,
            Hold hold,
            Instant startedAt,
            GatewayKeys.Access access,
            List<Candidate> candidates,
            List<RouteTrace.Attempt> attempts) {

        private RouteSelection selection() {
            return resolution.selection();
        }
    }
}
