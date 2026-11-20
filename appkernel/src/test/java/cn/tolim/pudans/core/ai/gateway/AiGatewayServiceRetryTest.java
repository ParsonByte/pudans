package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.gateway.agent.AiAgentAdapterRegistry;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.session.AiGatewaySessionService;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChatService;
import cn.tolim.pudans.core.ai.routing.AiRouteResolutionService;
import cn.tolim.pudans.core.ai.routing.RouteOutcome;
import cn.tolim.pudans.core.ai.routing.RoutePlan;
import cn.tolim.pudans.core.ai.routing.RouteRequest;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.RouteTrace;
import cn.tolim.pudans.core.ai.routing.SunkRouteRegistry;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository.NewChatRequest;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceRetryTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LangChain4jChatService chatService;

    @Mock
    AiRouteResolutionService routeResolutionService;

    @Mock
    AiSettingService settingService;

    @Mock
    AiGatewaySessionService sessionService;

    @Mock
    AiConversationService conversationService;

    @Mock
    AiAgentAdapterRegistry agentAdapterRegistry;

    @Mock
    AiMemoryContextBuilder memoryContextBuilder;

    @Mock
    AiWalletService walletService;

    @Mock
    AiChatRequestRepository chatRequestRepository;

    @Mock
    MemoryTaskScheduler memoryTaskScheduler;

    @Mock
    CircuitBreaker circuitBreaker;

    @Mock
    AiGatewayRateLimiter rateLimiter;

    @Mock
    AiGatewayKeyService keyService;

    private final SunkRouteRegistry sunkRouteRegistry = new SunkRouteRegistry();

    private AiProvider providerA;

    private AiProvider providerB;

    private AiProviderAccount accountA;

    private AiProviderAccount accountB;

    private AiModel modelA;

    private AiModel modelB;

    private AiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.getGateway().setInjectionEnabled(false);
        providerA = provider("provider-a");
        providerB = provider("provider-b");
        accountA = account("account-a", "provider-a");
        accountB = account("account-b", "provider-b");
        modelA = model("model-a", "provider-a");
        modelB = model("model-b", "provider-b");
        lenient()
                .when(keyService.authorize(any(), any()))
                .thenReturn(Mono.just(AiGatewayKeyService.Access.unrestricted()));
        lenient()
                .when(keyService.acquire(any()))
                .thenReturn(Mono.just(AiGatewayRateLimiter.Permit.empty()));
        lenient().when(keyService.release(any())).thenReturn(Mono.empty());
    }

    @Test
    void retriesRetryableErrorsUpToTheConfiguredMaximumWithExponentialBackoff() {
        var service = service(new AiGatewayRateLimiter(null));
        stubGateway(setting(2, 200, false), resolution(selectionA(), List.of(), true));
                when(chatService.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.error(upstream(HttpStatus.SERVICE_UNAVAILABLE)));
        when(walletService.release("req-1")).thenReturn(Mono.just(releaseResult()));

        StepVerifier.withVirtualTime(() -> service.complete(invocation()))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(599))
                .thenAwait(Duration.ofMillis(1))
                .expectError(ApiException.class)
                .verify();

        verify(chatService, times(3)).chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any());
    }

    @Test
    void doesNotRetryNonRetryableStatusesAndMovesToTheNextCandidate() {
        var service = service(new AiGatewayRateLimiter(null));
        stubGateway(setting(2, 1, false), resolution(selectionA(), List.of(selectionB()), false));
                when(chatService.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.error(upstream(HttpStatus.BAD_REQUEST)));
        when(walletService.release("req-1")).thenReturn(Mono.just(releaseResult()));

        StepVerifier.create(service.complete(invocation()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();

        verify(chatService, times(2)).chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any());
    }

    @Test
    void failsOverToTheNextCandidateAfterRetriesAreExhausted() {
        var service = service(new AiGatewayRateLimiter(null));
        stubGateway(setting(1, 1, false), resolution(selectionA(), List.of(selectionB()), true));
                when(chatService.chat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.error(upstream(HttpStatus.SERVICE_UNAVAILABLE)));
        when(chatService.chat(same(providerB), same(accountB), same(modelB), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(response()));
        when(walletService.settle(anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class), any()))
                .thenReturn(Mono.just(settleResult()));

        StepVerifier.create(service.complete(invocation()))
                .assertNext(result -> assertThat(result.meta().provider()).isEqualTo("provider-b"))
                .verifyComplete();

        verify(chatService, times(2)).chat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any());
        verify(chatService, times(1)).chat(same(providerB), same(accountB), same(modelB), eq(AiProvider.Protocol.OPENAI_CHAT), any());
    }

    @Test
    void recordsPurposeViaAndCallerKeyInTheRouteTrace() {
        var service = service(new AiGatewayRateLimiter(null));
        stubGateway(setting(0, 1, false), resolution(selectionA(), List.of(), true));
                when(chatService.chat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(response()));
        when(walletService.settle(anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class), any()))
                .thenReturn(Mono.just(settleResult()));
        var invocation = new AiGatewayInvocation(
                AiProvider.Protocol.OPENAI_CHAT,
                invocation().ir(),
                "user-1",
                "pudans-chat",
                "session-1",
                null,
                "req-1",
                "idem-1",
                null,
                "group-a",
                null,
                11L,
                null,
                "pat-1");

        StepVerifier.create(service.complete(invocation)).expectNextCount(1).verifyComplete();

        var captor = ArgumentCaptor.forClass(NewChatRequest.class);
        verify(chatRequestRepository).createChatRequest(captor.capture());
        assertThat(captor.getValue().callerKey()).isEqualTo("pat-1");
        var trace = JsonUtils.jsonToObject(captor.getValue().routeTrace(), Map.class);
        assertThat(trace)
                .containsEntry("purpose", "chat")
                .containsEntry("via", "openai-chat")
                .containsEntry("callerKey", "pat-1");
    }

    @Test
    void switchesToTheNextCandidateWhenTheLocalRateLimiterRejectsAndDoesNotRetryIt() {
        var service = service(rateLimiter);
        stubGateway(setting(2, 1, true), resolution(selectionA(), List.of(selectionB()), true));
                when(rateLimiter.acquire(same(providerA), same(accountA)))
                .thenReturn(Mono.error(rateLimited()));
        when(rateLimiter.acquire(same(providerB), same(accountB)))
                .thenReturn(Mono.just(AiGatewayRateLimiter.Permit.empty()));
        when(rateLimiter.acquireUser(anyString(), anyInt()))
                .thenReturn(Mono.just(AiGatewayRateLimiter.Permit.empty()));
        when(rateLimiter.release(any())).thenReturn(Mono.empty());
        when(chatService.chat(same(providerB), same(accountB), same(modelB), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(response()));
        when(walletService.settle(anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class), any()))
                .thenReturn(Mono.just(settleResult()));
        var subscribedA = new java.util.concurrent.atomic.AtomicBoolean();
        when(chatService.chat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.defer(() -> {
                    subscribedA.set(true);
                    return Mono.error(upstream(HttpStatus.SERVICE_UNAVAILABLE));
                }));

        StepVerifier.create(service.complete(invocation()))
                .assertNext(result -> assertThat(result.meta().provider()).isEqualTo("provider-b"))
                .verifyComplete();

        assertThat(subscribedA).isFalse();
        verify(rateLimiter).acquire(same(providerA), same(accountA));
        verify(rateLimiter).acquire(same(providerB), same(accountB));
    }

    @Test
    void marksAnUpstreamTooManyRequestsAsSunkWithoutRetryWhenRetriesAreDisabled() {
        var service = service(new AiGatewayRateLimiter(null));
        stubGateway(setting(0, 1, false), resolution(selectionA(), List.of(selectionB()), true));
                when(chatService.chat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.error(upstream(HttpStatus.TOO_MANY_REQUESTS)));
        when(chatService.chat(same(providerB), same(accountB), same(modelB), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(response()));
        when(walletService.settle(anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class), any()))
                .thenReturn(Mono.just(settleResult()));

        StepVerifier.create(service.complete(invocation()))
                .assertNext(result -> assertThat(result.meta().provider()).isEqualTo("provider-b"))
                .verifyComplete();

        verify(chatService, times(1)).chat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any());
        assertThat(sunkRouteRegistry.isSunk("provider-a", "account-a")).isTrue();
    }

    @Test
    void doesNotFailOverAfterTheFirstStreamedByte() {
        var service = service(new AiGatewayRateLimiter(null));
        stubGateway(setting(2, 1, false), resolution(selectionA(), List.of(selectionB()), false));
                when(chatService.streamChat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Flux.concat(
                        Flux.just(new AiStreamEvent("partial", null, null, null, null)),
                        Flux.error(upstream(HttpStatus.SERVICE_UNAVAILABLE))));
        when(walletService.settle(anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class), any()))
                .thenReturn(Mono.just(settleResult()));

        StepVerifier.create(service.stream(invocation()))
                .assertNext(event -> assertThat(event).isInstanceOf(AiGatewayEvent.Meta.class))
                .assertNext(event -> {
                    assertThat(event).isInstanceOf(AiGatewayEvent.Delta.class);
                    assertThat(((AiGatewayEvent.Delta) event).event().content()).isEqualTo("partial");
                })
                .assertNext(event -> {
                    assertThat(event).isInstanceOf(AiGatewayEvent.Error.class);
                    assertThat(((AiGatewayEvent.Error) event).partial()).isTrue();
                })
                .verifyComplete();

        verify(chatService, never()).streamChat(same(providerB), any(), any(), any(), any());
    }

    @Test
    void failsOverToTheNextCandidateBeforeTheFirstStreamedByte() {
        var service = service(new AiGatewayRateLimiter(null));
        stubGateway(setting(0, 1, false), resolution(selectionA(), List.of(selectionB()), false));
                when(chatService.streamChat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Flux.error(upstream(HttpStatus.SERVICE_UNAVAILABLE)));
        when(chatService.streamChat(same(providerB), same(accountB), same(modelB), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Flux.just(new AiStreamEvent("pong", null, null, null, "stop")));
        when(walletService.settle(anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class), any()))
                .thenReturn(Mono.just(settleResult()));

        StepVerifier.create(service.stream(invocation()))
                .assertNext(event -> assertThat(event).isInstanceOf(AiGatewayEvent.Meta.class))
                .assertNext(event -> assertThat(((AiGatewayEvent.Delta) event).event().content())
                        .isEqualTo("pong"))
                .assertNext(event -> assertThat(event).isInstanceOf(AiGatewayEvent.Usage.class))
                .assertNext(event -> assertThat(event).isInstanceOf(AiGatewayEvent.Done.class))
                .verifyComplete();

        verify(chatService, times(1)).streamChat(same(providerA), same(accountA), same(modelA), eq(AiProvider.Protocol.OPENAI_CHAT), any());
        verify(chatService, times(1)).streamChat(same(providerB), same(accountB), same(modelB), eq(AiProvider.Protocol.OPENAI_CHAT), any());
    }

    private AiGatewayService service(AiGatewayRateLimiter limiter) {
        return new AiGatewayService(
                client,
                chatService,
                routeResolutionService,
                settingService,
                sessionService,
                conversationService,
                agentAdapterRegistry,
                memoryContextBuilder,
                walletService,
                chatRequestRepository,
                memoryTaskScheduler,
                circuitBreaker,
                limiter,
                keyService,
                sunkRouteRegistry,
                properties,
                new AiMetrics(new SimpleMeterRegistry()));
    }

    private void stubGateway(
            AiSetting.Spec setting, AiRouteResolutionService.Resolution resolution) {
        when(settingService.spec()).thenReturn(Mono.just(setting));
        when(sessionService.bind(any())).thenReturn(Mono.just(session()));
        when(routeResolutionService.resolve(any())).thenReturn(Mono.just(resolution));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(providerA));
        lenient().when(client.get(AiProvider.class, "provider-b")).thenReturn(Mono.just(providerB));
        when(client.get(AiProviderAccount.class, "account-a")).thenReturn(Mono.just(accountA));
        lenient().when(client.get(AiProviderAccount.class, "account-b")).thenReturn(Mono.just(accountB));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(modelA));
        lenient().when(client.get(AiModel.class, "model-b")).thenReturn(Mono.just(modelB));
        when(conversationService.requireOwned(11L, "user-1")).thenReturn(Mono.just(conversation()));
        when(walletService.ensureWallet("user-1")).thenReturn(Mono.empty());
        when(walletService.freeze(anyString(), anyString(), any(BigDecimal.class), any(Duration.class)))
                .thenReturn(Mono.just(freezeResult()));
        when(chatRequestRepository.createChatRequest(any())).thenReturn(Mono.empty());
        when(chatRequestRepository.completeChatRequest(any())).thenReturn(Mono.empty());
    }

    private static AiSetting.Spec setting(int maxRetries, int backoffMillis, boolean rateLimitEnabled) {
        var setting = new AiSetting.Spec();
        setting.getGateway().setMaxRetries(maxRetries);
        setting.getGateway().setRetryBackoffMs(backoffMillis);
        setting.getGateway().setRateLimitEnabled(rateLimitEnabled);
        return setting;
    }

    private AiRouteResolutionService.Resolution resolution(
            RouteSelection selected, List<RouteSelection> fallbacks, boolean seatsAvailable) {
        var plan = new RoutePlan(
                selected,
                fallbacks,
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.ORDER,
                        null,
                        AiModel.Tier.CHEAP,
                        null,
                        null,
                        List.of(),
                        selected,
                        RouteOutcome.SELECTED,
                        List.of()));
        List<RouteSeat> seats = seatsAvailable
                ? List.of(seat(selectionA()), seat(selectionB()))
                : List.of();
        return new AiRouteResolutionService.Resolution(
                group(), null, RouteRequest.builder().build(), plan, seats);
    }

    private static RouteSelection selectionA() {
        return new RouteSelection("provider-a", "account-a", "model-a", AiModel.Tier.CHEAP);
    }

    private static RouteSelection selectionB() {
        return new RouteSelection("provider-b", "account-b", "model-b", AiModel.Tier.CHEAP);
    }

    private static RouteSeat seat(RouteSelection selection) {
        return RouteSeat.builder()
                .provider(selection.provider())
                .account(selection.account())
                .model(selection.model())
                .tier(selection.tier())
                .capabilities(Set.of(AiModel.Capability.CHAT))
                .build();
    }

    private static ApiException upstream(HttpStatus status) {
        return new ApiException(status, "ai-gateway-upstream-error", "上游调用失败。");
    }

    private static ApiException rateLimited() {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                AiGatewayRateLimiter.CODE_RATE_LIMITED,
                "AI 网关限流： provider provider-a 超过速率或并发限制。");
    }

    private static AiGatewayInvocation invocation() {
        var ir = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hi", null)))),
                List.of(),
                new AiRequestParams(null, null, 128, null, null, null, null, null),
                false);
        return new AiGatewayInvocation(
                AiProvider.Protocol.OPENAI_CHAT,
                ir,
                "user-1",
                "pudans-chat",
                "session-1",
                null,
                "req-1",
                "idem-1",
                null,
                "group-a",
                null,
                11L);
    }

    private static AiRouteGroup group() {
        var group = new AiRouteGroup();
        group.setMetadata(metadata("group-a"));
        return group;
    }

    private static AiProvider provider(String name) {
        var provider = new AiProvider();
        provider.setMetadata(metadata(name));
        provider.getSpec().setDisplayName(name);
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setMaxRPM(0);
        provider.getSpec().setMaxConcurrency(0);
        return provider;
    }

    private static AiProviderAccount account(String name, String provider) {
        var account = new AiProviderAccount();
        account.setMetadata(metadata(name));
        account.getSpec().setProvider(provider);
        account.getSpec().setMaxRPM(0);
        account.getSpec().setMaxConcurrency(0);
        return account;
    }

    private static AiModel model(String name, String provider) {
        var model = new AiModel();
        model.setMetadata(metadata(name));
        model.getSpec().setProvider(provider);
        model.getSpec().setModelCode(name);
        model.getSpec().setTier(AiModel.Tier.CHEAP);
        model.getSpec().setMaxOutputTokens(1000);
        return model;
    }

    private static GatewaySession session() {
        return new GatewaySession(
                1L,
                "default",
                "user-1",
                "pudans-chat",
                "session-1",
                null,
                "team-a",
                "agent-a",
                null,
                11L,
                "ACTIVE",
                Instant.now(),
                Instant.now());
    }

    private static Conversation conversation() {
        return new Conversation(
                11L,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                null,
                null,
                "group-a",
                "ACTIVE",
                null,
                null,
                0,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now());
    }

    private static AiChatResponse response() {
        return new AiChatResponse(
                "resp-1",
                "model-b",
                new AiChatMessage("assistant", List.of(new AiContentPart.Text("pong", null))),
                new AiTokenUsage(10, 10, null, null),
                "stop");
    }

    private static AiWalletService.FreezeResult freezeResult() {
        return new AiWalletService.FreezeResult(
                "req-1", "user-1", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, false);
    }

    private static AiWalletService.SettleResult settleResult() {
        return new AiWalletService.SettleResult(
                "req-1", "user-1", BigDecimal.ONE, BigDecimal.valueOf(9), "settle:req-1", false);
    }

    private static AiWalletService.ReleaseResult releaseResult() {
        return new AiWalletService.ReleaseResult("req-1", true, "release:req-1");
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}