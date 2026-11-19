package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
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
import cn.tolim.pudans.core.ai.gateway.session.AiGatewaySessionService;
import cn.tolim.pudans.core.ai.gateway.session.AiGatewaySessionService.BindRequest;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.ai.provider.AiProviderAdapterRegistry;
import cn.tolim.pudans.core.ai.routing.AiRouteResolutionService;
import cn.tolim.pudans.core.ai.routing.RoutePlan;
import cn.tolim.pudans.core.ai.routing.RouteRequest;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitFailure;
import cn.tolim.pudans.core.ai.wallet.AiPointsCalculator;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.core.ai.wallet.AiWalletUsage;
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
import java.util.List;
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
public class AiGatewayService {

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

    private final AiProviderAdapterRegistry adapterRegistry;

    private final AiRouteResolutionService routeResolutionService;

    private final AiSettingService settingService;

    private final AiGatewaySessionService sessionService;

    private final AiConversationService conversationService;

    private final AiAgentAdapterRegistry agentAdapterRegistry;

    private final AiMemoryContextBuilder memoryContextBuilder;

    private final AiWalletService walletService;

    private final AiChatRequestRepository chatRequestRepository;

    private final MemoryTaskScheduler memoryTaskScheduler;

    private final CircuitBreaker circuitBreaker;

    private final AiProperties properties;

    private final AiMetrics metrics;

    public Mono<AiGatewayResult> complete(AiGatewayInvocation invocation) {
        return prepare(invocation)
                .flatMap(prepared -> chat(prepared, candidates(prepared.resolution().plan()), 0, null)
                        .onErrorResume(error -> fail(prepared, error).then(Mono.error(upstreamError(error))))
                        .flatMap(attempt -> succeed(prepared, attempt)));
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
                            candidates(prepared.resolution().plan()),
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
            return Flux.concat(Mono.just(new Meta(meta)), deltas, tail)
                    .onErrorResume(error -> finished.get()
                            ? Flux.empty()
                            : streamFail(prepared, actual.get(), content.toString(), error)
                                    .doOnNext(event -> finished.set(true)))
                    .doOnCancel(() -> {
                        if (!finished.get()) {
                            streamCancel(prepared, content.toString()).subscribe();
                        }
                    });
        }));
    }

    private Mono<Prepared> prepare(AiGatewayInvocation invocation) {
        if (!properties.getGateway().isEnabled()) {
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "ai-disabled", "AI 功能未启用。"));
        }
        return settingService.spec().flatMap(setting -> prepare(invocation, setting));
    }

    private Mono<Prepared> prepare(AiGatewayInvocation invocation, AiSetting.Spec setting) {
        return bind(invocation, null).flatMap(session -> inject(invocation, setting, session)
                .flatMap(injected -> routeResolutionService
                        .resolve(new AiRouteResolutionService.Input(
                                injected.modeCode(),
                                injected.routeGroupCode(),
                                null,
                                injected.ir(),
                                setting,
                                null))
                        .doOnNext(this::recordRoutingDecision)
                        .doOnError(error -> recordRouteExhausted(invocation, error))
                        .flatMap(resolution -> prepare(injected, setting, session, resolution)
                                .doOnError(this::recordWalletInsufficient))));
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
                invocation.conversationId());
    }

    private Mono<Prepared> prepare(
            AiGatewayInvocation invocation,
            AiSetting.Spec setting,
            GatewaySession session,
            AiRouteResolutionService.Resolution resolution) {
        return load(resolution.selection()).flatMap(resources -> conversation(invocation, resolution, session)
                .flatMap(conversation -> bind(invocation, conversation.id())
                        .flatMap(bound -> hold(invocation, setting, resolution, resources)
                                .flatMap(hold -> createChatRequest(invocation, resolution, bound, conversation)
                                        .onErrorResume(error -> release(hold).then(Mono.error(error)))
                                        .thenReturn(new Prepared(
                                                invocation,
                                                setting,
                                                bound,
                                                resolution,
                                                resources,
                                                conversation,
                                                hold,
                                                Instant.now()))))));
    }

    private Mono<GatewaySession> bind(AiGatewayInvocation invocation, @Nullable Long conversationId) {
        return sessionService.bind(new BindRequest(
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
            AiRouteResolutionService.Resolution resolution,
            GatewaySession session) {
        if (invocation.conversationId() != null) {
            return conversationService.requireOwned(invocation.conversationId(), invocation.userId());
        }
        return conversationService.createOrGet(new NewConversation(
                AiGatewaySessionService.DEFAULT_SPACE,
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
            AiRouteResolutionService.Resolution resolution,
            Resources resources) {
        var request = resolution.request();
        var seat = seatOf(resolution, resolution.selection());
        var free = seat != null && (seat.isFree() || seat.isFreePlan());
        var maxOutput = request.getMaxOutputTokens() > 0
                ? request.getMaxOutputTokens()
                : defaultMaxOutput(resources.model());
        var amount = AiPointsCalculator.estimate(resources.model(), free, request.getPromptTokens(), maxOutput);
        if (amount.signum() <= 0) {
            return Mono.just(new Hold(null, ZERO, free));
        }
        var billing = setting.getBilling() == null ? new AiSetting.Billing() : setting.getBilling();
        return walletService
                .ensureWallet(invocation.userId())
                .then(walletService.freeze(
                        invocation.requestId(),
                        invocation.userId(),
                        amount,
                        Duration.ofSeconds(Math.max(1, billing.getFreezeExpirySeconds()))))
                .map(freeze -> new Hold(freeze, amount, free));
    }

    private Mono<Void> createChatRequest(
            AiGatewayInvocation invocation,
            AiRouteResolutionService.Resolution resolution,
            GatewaySession session,
            Conversation conversation) {
        var selection = resolution.selection();
        return chatRequestRepository
                .createChatRequest(new NewChatRequest(
                        invocation.requestId(),
                        AiGatewaySessionService.DEFAULT_SPACE,
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
                        selection.tier() == null ? null : selection.tier().name()))
                .then();
    }

    private Mono<Attempted> chat(
            Prepared prepared,
            List<RouteSelection> candidates,
            int index,
            @Nullable Throwable lastError) {
        if (index >= candidates.size()) {
            metrics.providerExhausted(prepared.resolution().routeGroup().getMetadata().getName());
            return Mono.error(lastError == null
                    ? upstreamError(new IllegalStateException("没有可用候选"))
                    : lastError);
        }
        var selection = candidates.get(index);
        return load(selection)
                .flatMap(resources -> adapterRegistry
                        .require(resources.provider().getSpec().getProtocol())
                        .chat(resources.provider(), resources.account(), resources.model(), prepared.invocation().ir())
                        .doOnSuccess(response -> recordSuccess(resources))
                        .map(response -> new Attempted(response, resources)))
                .onErrorResume(error -> {
                    if (!(error instanceof ExtensionNotFoundException)) {
                        recordFailure(selection, error);
                    }
                    return chat(prepared, candidates, index + 1, error);
                });
    }

    private Flux<AiStreamEvent> streamChat(
            Prepared prepared,
            List<RouteSelection> candidates,
            int index,
            AtomicBoolean emitted,
            AtomicReference<Resources> actual) {
        if (index >= candidates.size()) {
            metrics.providerExhausted(prepared.resolution().routeGroup().getMetadata().getName());
            return Flux.error(upstreamError(new IllegalStateException("没有可用候选")));
        }
        var selection = candidates.get(index);
        return load(selection)
                .flatMapMany(resources -> {
                    actual.set(resources);
                    return adapterRegistry
                            .require(resources.provider().getSpec().getProtocol())
                            .streamChat(
                                    resources.provider(),
                                    resources.account(),
                                    resources.model(),
                                    prepared.invocation().ir())
                            .doOnNext(event -> emitted.set(true))
                            .doOnComplete(() -> recordSuccess(resources));
                })
                .onErrorResume(error -> {
                    if (!(error instanceof ExtensionNotFoundException)) {
                        recordFailure(selection, error);
                    }
                    if (!emitted.get()) {
                        return streamChat(prepared, candidates, index + 1, emitted, actual);
                    }
                    return Flux.error(error);
                });
    }

    private Mono<AiGatewayResult> succeed(Prepared prepared, Attempted attempt) {
        var usage = resolveUsage(prepared, attempt.response().usage(), textOf(attempt.response().message()));
        var points = charge(attempt.resources(), prepared.hold(), usage);
        var billingSource = billingSource(attempt.response().usage());
        var walletUsage = walletUsage(attempt.resources(), prepared.selection(), usage, billingSource);
        var settle = prepared.hold().freeze() == null
                ? Mono.empty()
                : walletService
                        .settle(
                                prepared.invocation().requestId(),
                                prepared.invocation().userId(),
                                prepared.hold().amount(),
                                points,
                                walletUsage)
                        .then();
        var latencyDuration = Duration.between(prepared.startedAt(), Instant.now());
        var latency = (int) latencyDuration.toMillis();
        recordSuccessMetrics(prepared, attempt.resources(), usage, points, latencyDuration);
        return settle.then(complete(prepared, usage, points, billingSource, STATUS_SUCCEEDED, null,
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
                        points,
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
        return conversationService
                .history(prepared.conversation(), 200)
                .collectList()
                .flatMap(history -> {
                    var turn = history.isEmpty() ? 1 : history.getLast().turnIndex() + 1;
                    return conversationService
                            .appendUserMessage(
                                    prepared.conversation(), turn, 0, userText, invocation.requestId())
                            .flatMap(userMessage -> conversationService
                                    .appendAssistantMessage(
                                            prepared.conversation(),
                                            turn,
                                            1,
                                            assistantText,
                                            invocation.requestId(),
                                            resources.model().getSpec().getModelCode(),
                                            usage.outputTokens())
                                    .flatMap(assistantMessage -> conversationService
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
        var release = prepared.hold().freeze() == null
                ? Mono.empty()
                : walletService.release(prepared.invocation().requestId()).then();
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
        var points = charge(resources, prepared.hold(), usage);
        var billingSource = billingSource(reported);
        var walletUsage = walletUsage(resources, prepared.selection(), usage, billingSource);
        var settle = prepared.hold().freeze() == null
                ? Mono.empty()
                : walletService
                        .settle(
                                prepared.invocation().requestId(),
                                prepared.invocation().userId(),
                                prepared.hold().amount(),
                                points,
                                walletUsage)
                        .then();
        var latencyDuration = Duration.between(prepared.startedAt(), Instant.now());
        var latency = (int) latencyDuration.toMillis();
        recordSuccessMetrics(prepared, resources, usage, points, latencyDuration);
        return settle.thenMany(complete(
                        prepared, usage, points, billingSource, STATUS_SUCCEEDED, null, BILLING_SETTLED,
                        null, latency, null)
                .thenMany(captureExternal(prepared, content, resources, usage))
                .thenMany(Flux.just(
                        new Usage(usage, points, billingSource),
                        new Done("msg-" + prepared.invocation().requestId(), finishReason, false))));
    }

    private Flux<AiGatewayEvent> streamFail(
            Prepared prepared, @Nullable Resources actual, String content, Throwable error) {
        var resources = actual == null ? prepared.resources() : actual;
        var partial = !content.isEmpty();
        var usage = resolveUsage(prepared, null, content);
        var points = partial ? charge(resources, prepared.hold(), usage) : ZERO;
        var billingStatus = partial ? BILLING_SETTLED : BILLING_RELEASED;
        var billingSource = partial ? SOURCE_ESTIMATED : null;
        var action = prepared.hold().freeze() == null
                ? Mono.empty()
                : partial
                        ? walletService
                                .settle(
                                        prepared.invocation().requestId(),
                                        prepared.invocation().userId(),
                                        prepared.hold().amount(),
                                        points,
                                        walletUsage(resources, prepared.selection(), usage, billingSource))
                                .then()
                        : walletService.release(prepared.invocation().requestId()).then();
        var mapped = upstreamError(error);
        var latencyDuration = Duration.between(prepared.startedAt(), Instant.now());
        var latency = (int) latencyDuration.toMillis();
        recordFailureMetrics(prepared, latencyDuration);
        return action.thenMany(complete(
                        prepared, usage, points, billingSource, STATUS_FAILED, codeOf(mapped),
                        billingStatus, null, latency, null)
                .thenMany(Flux.just(new Error(
                        codeOf(mapped), reasonOf(mapped), mapped.getStatusCode().is5xxServerError(), partial))));
    }

    private Mono<Void> streamCancel(Prepared prepared, String content) {
        if (content.isEmpty()) {
            return prepared.hold().freeze() == null
                    ? Mono.empty()
                    : walletService.release(prepared.invocation().requestId()).then();
        }
        var usage = resolveUsage(prepared, null, content);
        var points = charge(prepared.resources(), prepared.hold(), usage);
        Mono<Void> settle = prepared.hold().freeze() == null
                ? Mono.empty()
                : walletService
                        .settle(
                                prepared.invocation().requestId(),
                                prepared.invocation().userId(),
                                prepared.hold().amount(),
                                points,
                                walletUsage(prepared.resources(), prepared.selection(), usage, SOURCE_ESTIMATED))
                        .then();
        return settle;
    }

    private Mono<Void> complete(
            Prepared prepared,
            @Nullable AiTokenUsage usage,
            @Nullable BigDecimal points,
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
                        points,
                        billingSource,
                        billingStatus,
                        latencyMillis,
                        ttftMillis,
                        tps,
                        billingSource,
                        upstreamRequestId))
                .then();
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

    private void recordFailure(RouteSelection selection, Throwable error) {
        log.warn("AI 供应商调用失败：{}/{}", selection.provider(), selection.account(), error);
        circuitBreaker.recordFailure(selection.provider(), CircuitFailure.defaultFailure());
        circuitBreaker.recordFailure(selection.account(), CircuitFailure.defaultFailure());
    }

    private void recordSuccessMetrics(
            Prepared prepared, Resources resources, AiTokenUsage usage, BigDecimal points, Duration latency) {
        var protocol = protocolOf(prepared.invocation());
        var provider = resources.provider().getMetadata().getName();
        var model = resources.model().getSpec().getModelCode();
        metrics.gatewayRequest(protocol, prepared.invocation().agentSource(), AiMetrics.STATUS_SUCCESS);
        metrics.gatewayLatency(protocol, AiMetrics.STATUS_SUCCESS, latency);
        metrics.gatewayTokens(provider, model, usage.inputTokens(), usage.outputTokens(), cacheTokens(usage));
        metrics.gatewayPoints(points, provider, model);
    }

    private void recordFailureMetrics(Prepared prepared, Duration latency) {
        var protocol = protocolOf(prepared.invocation());
        metrics.gatewayRequest(protocol, prepared.invocation().agentSource(), AiMetrics.STATUS_FAILED);
        metrics.gatewayLatency(protocol, AiMetrics.STATUS_FAILED, latency);
    }

    private void recordRoutingDecision(AiRouteResolutionService.Resolution resolution) {
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

    private void recordWalletInsufficient(Throwable error) {
        if (error instanceof ApiException apiException
                && apiException.getStatusCode().value() == HttpStatus.PAYMENT_REQUIRED.value()) {
            metrics.walletInsufficient();
        }
    }

    private static String protocolOf(AiGatewayInvocation invocation) {
        return invocation.protocol() == null ? null : invocation.protocol().name();
    }

    private static List<RouteSelection> candidates(RoutePlan plan) {
        var candidates = new ArrayList<RouteSelection>();
        if (plan.selected() != null) {
            candidates.add(plan.selected());
        }
        candidates.addAll(plan.fallbackSeats());
        return candidates;
    }

    private static RouteSeat seatOf(AiRouteResolutionService.Resolution resolution, RouteSelection selection) {
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

    private static BigDecimal charge(Resources resources, Hold hold, AiTokenUsage usage) {
        return AiPointsCalculator.charge(
                resources.model(),
                hold.free() || freeProvider(resources),
                nullSafe(usage.inputTokens()),
                nullSafe(usage.cacheReadTokens()) + nullSafe(usage.cacheWriteTokens()),
                nullSafe(usage.outputTokens()));
    }

    private static boolean freeProvider(Resources resources) {
        return (resources.provider().getSpec() != null && resources.provider().getSpec().isFree())
                || (resources.account().getSpec() != null && resources.account().getSpec().isFreePlan());
    }

    private static AiWalletUsage walletUsage(
            Resources resources, RouteSelection selection, AiTokenUsage usage, @Nullable String billingSource) {
        return new AiWalletUsage(
                selection.account(),
                resources.model().getSpec().getModelCode(),
                selection.tier() == null ? null : selection.tier().name(),
                usage.inputTokens(),
                nullSafe(usage.cacheReadTokens()) + nullSafe(usage.cacheWriteTokens()),
                usage.outputTokens(),
                null,
                billingSource);
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
        return hold.freeze() == null ? Mono.empty() : walletService.release(hold.freeze().requestId()).then();
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

    private record Hold(AiWalletService.FreezeResult freeze, BigDecimal amount, boolean free) {}

    private record Attempted(AiChatResponse response, Resources resources) {}

    private record Prepared(
            AiGatewayInvocation invocation,
            AiSetting.Spec setting,
            GatewaySession session,
            AiRouteResolutionService.Resolution resolution,
            Resources resources,
            Conversation conversation,
            Hold hold,
            Instant startedAt) {

        private RouteSelection selection() {
            return resolution.selection();
        }
    }
}
