package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiPlan;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
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
import cn.tolim.pudans.core.ai.plan.PlanQuotaService;
import cn.tolim.pudans.core.ai.plan.PlanResolutionService;
import cn.tolim.pudans.core.ai.plan.PlanUsageSnapshot;
import cn.tolim.pudans.core.ai.provider.ProviderSpendGuard;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChatService;
import cn.tolim.pudans.core.ai.routing.AiRouteResolutionService;
import cn.tolim.pudans.core.ai.plan.PlanQuotaExceededException;
import cn.tolim.pudans.core.ai.plan.PlanWindowKind;
import cn.tolim.pudans.core.ai.routing.RouteOutcome;
import cn.tolim.pudans.core.ai.routing.RoutePlan;
import cn.tolim.pudans.core.ai.routing.RouteRequest;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.RouteTrace;
import cn.tolim.pudans.core.ai.routing.SunkRouteRegistry;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.team.AiTeamQuotaService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository.ChatCompletion;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository.NewChatRequest;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGatewayPlanBillingTest {

    private static final String REQUEST_ID = "req-1";

    private static final long ESTIMATED_UNITS = 756L;

    private static final long ACTUAL_UNITS = 25L;

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
    PlanResolutionService planResolutionService;

    @Mock
    PlanQuotaService planQuotaService;

    @Mock
    AiChatRequestRepository chatRequestRepository;

    @Mock
    MemoryTaskScheduler memoryTaskScheduler;

    @Mock
    CircuitBreaker circuitBreaker;

    @Mock
    ProviderSpendGuard providerSpendGuard;

    @Mock
    AiTeamQuotaService teamQuotaService;

    @Mock
    AiGatewayKeyService keyService;

    private AiGatewayService service;

    @BeforeEach
    void setUp() {
        var properties = new AiProperties();
        properties.getGateway().setInjectionEnabled(false);
        service = new AiGatewayService(
                client,
                chatService,
                routeResolutionService,
                settingService,
                sessionService,
                conversationService,
                agentAdapterRegistry,
                memoryContextBuilder,
                planResolutionService,
                planQuotaService,
                chatRequestRepository,
                memoryTaskScheduler,
                circuitBreaker,
                new AiGatewayRateLimiter(null),
                keyService,
                new SunkRouteRegistry(),
                properties,
                new AiMetrics(new SimpleMeterRegistry()),
                providerSpendGuard,
                teamQuotaService);
        lenient().when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        lenient().when(sessionService.bind(any())).thenReturn(Mono.just(session()));
        lenient().when(keyService.authorize(any(), any()))
                .thenReturn(Mono.just(AiGatewayKeyService.Access.unrestricted()));
        lenient().when(keyService.acquire(any()))
                .thenReturn(Mono.just(AiGatewayRateLimiter.Permit.empty()));
        lenient().when(keyService.release(any())).thenReturn(Mono.empty());
        lenient().when(conversationService.requireOwned(11L, "user-1")).thenReturn(Mono.just(conversation()));
        lenient().when(providerSpendGuard.check(any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(teamQuotaService.check(any(), any())).thenReturn(Mono.empty());
        lenient().when(planResolutionService.resolve("user-1")).thenReturn(Mono.just(resolved(spec -> {})));
        lenient()
                .when(planQuotaService.reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(Mono.just(reserveResult()));
        lenient().when(planQuotaService.settle(anyString(), anyLong(), any())).thenReturn(Mono.just(settleResult()));
        lenient().when(planQuotaService.release(anyString())).thenReturn(Mono.just(releaseResult()));
        lenient().when(chatRequestRepository.createChatRequest(any())).thenReturn(Mono.empty());
        lenient().when(chatRequestRepository.completeChatRequest(any())).thenReturn(Mono.empty());
    }

    @Test
    void reservesAndSettlesStandardUnitsForUserInvocations() {
        stubProviderA(false);
        when(chatService.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(response()));

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.USER)))
                .expectNextCount(1)
                .verifyComplete();

        var planCaptor = ArgumentCaptor.forClass(AiPlan.class);
        verify(planQuotaService)
                .reserve(
                        eq(REQUEST_ID),
                        eq("user-1"),
                        planCaptor.capture(),
                        eq(ESTIMATED_UNITS),
                        eq("CHAT"),
                        eq("team-a"),
                        eq(REQUEST_ID));
        assertThat(planCaptor.getValue().getMetadata().getName()).isEqualTo("standard");
        verify(teamQuotaService).check("team-a", BigDecimal.valueOf(ESTIMATED_UNITS));

        var snapshotCaptor = ArgumentCaptor.forClass(PlanUsageSnapshot.class);
        verify(planQuotaService).settle(eq(REQUEST_ID), eq(ACTUAL_UNITS), snapshotCaptor.capture());
        var snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.provider()).isEqualTo("provider-a");
        assertThat(snapshot.providerAccount()).isEqualTo("account-a");
        assertThat(snapshot.modelCode()).isEqualTo("model-a-code");
        assertThat(snapshot.tier()).isEqualTo(AiModel.Tier.CHEAP);
        assertThat(snapshot.inputTokens()).isEqualTo(10);
        assertThat(snapshot.outputTokens()).isEqualTo(10);

        var requestCaptor = ArgumentCaptor.forClass(NewChatRequest.class);
        verify(chatRequestRepository).createChatRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue().planId()).isEqualTo("standard");
        assertThat(requestCaptor.getValue().billingScope()).isEqualTo("USER");

        var completionCaptor = ArgumentCaptor.forClass(ChatCompletion.class);
        verify(chatRequestRepository).completeChatRequest(completionCaptor.capture());
        assertThat(completionCaptor.getValue().units()).isEqualTo(ACTUAL_UNITS);
    }

    @Test
    void releasesTheReservationWhenTheProviderFails() {
        var setting = new AiSetting.Spec();
        setting.getGateway().setMaxRetries(0);
        when(settingService.spec()).thenReturn(Mono.just(setting));
        stubProviderA(false);
        when(chatService.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.error(new IllegalStateException("upstream down")));

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.USER)))
                .expectError(ApiException.class)
                .verify();

        verify(planQuotaService).release(REQUEST_ID);
        verify(planQuotaService, never()).settle(anyString(), anyLong(), any());
    }

    @Test
    void bypassesPlanBillingForSystemInvocations() {
        stubProviderA(false);
        when(chatService.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(response()));

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.SYSTEM)))
                .expectNextCount(1)
                .verifyComplete();

        verify(planResolutionService, never()).resolve(anyString());
        verify(planQuotaService, never())
                .reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any());
        verify(planQuotaService, never()).settle(anyString(), anyLong(), any());
        verify(planQuotaService, never()).release(anyString());
        verify(teamQuotaService, never()).check(any(), any());
        verify(providerSpendGuard).check(any(), any(), any());
    }

    @Test
    void doesNotReserveOrSettleForFreeProviders() {
        stubProviderA(true);
        when(chatService.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(response()));

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.USER)))
                .expectNextCount(1)
                .verifyComplete();

        verify(planQuotaService, never())
                .reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any());
        verify(planQuotaService, never()).settle(anyString(), anyLong(), any());

        var completionCaptor = ArgumentCaptor.forClass(ChatCompletion.class);
        verify(chatRequestRepository).completeChatRequest(completionCaptor.capture());
        assertThat(completionCaptor.getValue().units()).isEqualTo(0L);
    }

    @Test
    void rejectsWeeklyExhaustionWithoutDowngrade() {
        stubProviderA(false);
        when(planQuotaService.reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(Mono.error(quotaError(PlanWindowKind.WEEKLY)));

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.USER)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var api = (ApiException) error;
                    assertThat(api.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(api.getCode()).isEqualTo(PlanQuotaService.CODE_QUOTA_EXCEEDED);
                })
                .verify();

        verify(routeResolutionService).resolve(any());
        verify(chatService, never()).chat(any(), any(), any(), any(), any());
    }

    @Test
    void downgradesOnceWhenTheFiveHourWindowIsExhausted() {
        when(planResolutionService.resolve("user-1")).thenReturn(Mono.just(resolved(spec -> {
            spec.getFallback().setOnFiveHourExhausted(AiPlan.FallbackAction.DOWNGRADE);
            spec.getFallback().setDowngradeRouteGroup("cheap-group");
        })));
        var selectionA = selectionA();
        var selectionB = selectionB();
        when(routeResolutionService.resolve(any())).thenAnswer(call -> {
            var input = call.getArgument(0, AiRouteResolutionService.Input.class);
            return Mono.just("cheap-group".equals(input.routeGroupCode())
                    ? resolution("cheap-group", selectionB)
                    : resolution("group-a", selectionA));
        });
        stubProvider("provider-a", "account-a", "model-a", provider("provider-a", false));
        stubProvider("provider-b", "account-b", "model-b", provider("provider-b", false));
        when(planQuotaService.reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(Mono.error(quotaError(PlanWindowKind.FIVE_HOUR)))
                .thenReturn(Mono.just(reserveResult()));
        when(chatService.chat(any(), any(), any(), any(), any())).thenReturn(Mono.just(response()));

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.USER)))
                .assertNext(result -> assertThat(result.meta().provider()).isEqualTo("provider-b"))
                .verifyComplete();

        verify(planQuotaService, times(2))
                .reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any());
        verify(chatService, times(1)).chat(any(), any(), any(), any(), any());
    }

    @Test
    void deniesModelsOutsidePlanAccess() {
        when(planResolutionService.resolve("user-1"))
                .thenReturn(Mono.just(resolved(spec -> spec.getAccess().setModels(List.of("model-b")))));
        stubProviderA(false);

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.USER)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var api = (ApiException) error;
                    assertThat(api.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(api.getCode()).isEqualTo(AiPlan.CODE_MODEL_DENIED);
                })
                .verify();

        verify(chatService, never()).chat(any(), any(), any(), any(), any());
        verify(planQuotaService, never())
                .reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any());
    }

    @Test
    void deniesFeaturesOutsidePlanAccess() {
        when(planResolutionService.resolve("user-1")).thenReturn(
                Mono.just(resolved(spec -> spec.getAccess().setFeatures(List.of(AiPlan.Feature.AGENT)))));
        stubProviderA(false);

        StepVerifier.create(service.complete(invocation(AiPlan.BillingScope.USER)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo(AiPlan.CODE_MODEL_DENIED);
                })
                .verify();

        verify(chatService, never()).chat(any(), any(), any(), any(), any());
    }

    @Test
    void releasesTheReservationOnStreamCancelBeforeTheFirstDelta() {
        stubProviderA(false);

        StepVerifier.create(service.stream(invocation(AiPlan.BillingScope.USER)))
                .assertNext(event -> assertThat(event).isInstanceOf(AiGatewayEvent.Meta.class))
                .thenCancel()
                .verify();

        verify(planQuotaService, timeout(2000)).release(REQUEST_ID);
        verify(planQuotaService, never()).settle(anyString(), anyLong(), any());
    }

    private void stubProviderA(boolean free) {
        var provider = provider("provider-a", free);
        stubProvider("provider-a", "account-a", "model-a", provider);
        lenient().when(routeResolutionService.resolve(any()))
                .thenReturn(Mono.just(resolution("group-a", selectionA())));
    }

    private void stubProvider(String providerName, String accountName, String modelName, AiProvider provider) {
        lenient().when(client.get(AiProvider.class, providerName)).thenReturn(Mono.just(provider));
        lenient().when(client.get(AiProviderAccount.class, accountName))
                .thenReturn(Mono.just(account(accountName, providerName)));
        lenient().when(client.get(AiModel.class, modelName)).thenReturn(Mono.just(model(modelName, providerName)));
    }

    private static PlanResolutionService.ResolvedPlan resolved(Consumer<AiPlan.Spec> customizer) {
        var plan = new AiPlan();
        plan.setMetadata(metadata("standard"));
        plan.getSpec().setDisplayName("标准套餐");
        plan.getSpec().getQuota().getFiveHour().setLimit(3_000_000);
        plan.getSpec().getQuota().getWeekly().setLimit(20_000_000);
        customizer.accept(plan.getSpec());
        return new PlanResolutionService.ResolvedPlan(
                "standard", "标准套餐", "User/user-1", plan);
    }

    private static PlanQuotaService.ReserveResult reserveResult() {
        return new PlanQuotaService.ReserveResult(
                REQUEST_ID, "user-1", "standard", ESTIMATED_UNITS, "five-hour-1", "weekly-1", false);
    }

    private static PlanQuotaService.SettleResult settleResult() {
        return new PlanQuotaService.SettleResult(REQUEST_ID, ACTUAL_UNITS, false);
    }

    private static PlanQuotaService.ReleaseResult releaseResult() {
        return new PlanQuotaService.ReleaseResult(REQUEST_ID, true);
    }

    private static PlanQuotaExceededException quotaError(PlanWindowKind kind) {
        return new PlanQuotaExceededException(kind, 0, Instant.now());
    }

    private static AiGatewayInvocation invocation(AiPlan.BillingScope scope) {
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
                REQUEST_ID,
                "idem-1",
                null,
                "group-a",
                null,
                11L,
                null,
                null,
                scope);
    }

    private static AiRouteResolutionService.Resolution resolution(String group, RouteSelection selection) {
        var routeGroup = new AiRouteGroup();
        routeGroup.setMetadata(metadata(group));
        var plan = new RoutePlan(
                selection,
                List.of(),
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.SMART,
                        null,
                        selection.tier(),
                        null,
                        null,
                        List.of(),
                        selection,
                        RouteOutcome.SELECTED,
                        List.of()));
        var request = RouteRequest.builder().promptTokens(1000).maxOutputTokens(128).build();
        return new AiRouteResolutionService.Resolution(routeGroup, null, request, plan, List.of());
    }

    private static RouteSelection selectionA() {
        return new RouteSelection("provider-a", "account-a", "model-a", AiModel.Tier.CHEAP);
    }

    private static RouteSelection selectionB() {
        return new RouteSelection("provider-b", "account-b", "model-b", AiModel.Tier.CHEAP);
    }

    private static AiProvider provider(String name, boolean free) {
        var provider = new AiProvider();
        provider.setMetadata(metadata(name));
        provider.getSpec().setDisplayName(name);
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setFree(free);
        return provider;
    }

    private static AiProviderAccount account(String name, String provider) {
        var account = new AiProviderAccount();
        account.setMetadata(metadata(name));
        account.getSpec().setProvider(provider);
        account.getSpec().setFreePlan(false);
        return account;
    }

    private static AiModel model(String name, String provider) {
        var model = new AiModel();
        model.setMetadata(metadata(name));
        model.getSpec().setProvider(provider);
        model.getSpec().setModelCode(name + "-code");
        model.getSpec().setTier(AiModel.Tier.CHEAP);
        model.getSpec().setMaxOutputTokens(1000);
        return model;
    }

    private static AiChatResponse response() {
        return new AiChatResponse(
                "resp-1",
                "model-a-code",
                new AiChatMessage("assistant", List.of(new AiContentPart.Text("pong", null))),
                new AiTokenUsage(10, 10, null, null),
                "stop");
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

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}