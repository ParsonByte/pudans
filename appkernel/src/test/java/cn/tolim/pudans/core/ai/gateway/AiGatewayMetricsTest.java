package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.ai.resource.AiRouteGroup;
import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.conversation.Conversations;
import cn.tolim.pudans.core.ai.gateway.agent.AiAgentAdapterRegistry;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.session.GatewaySessions;
import cn.tolim.pudans.core.ai.memory.AiMemoryContextBuilder;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.metrics.AiMetrics;
import cn.tolim.pudans.core.ai.plan.PlanQuotas;
import cn.tolim.pudans.core.ai.plan.PlanResolution;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChat;
import cn.tolim.pudans.core.ai.routing.RouteResolution;
import cn.tolim.pudans.core.ai.routing.RouteOutcome;
import cn.tolim.pudans.core.ai.routing.RoutePlan;
import cn.tolim.pudans.core.ai.routing.RouteRequest;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.RouteTrace;
import cn.tolim.pudans.core.ai.routing.SunkRouteRegistry;
import cn.tolim.pudans.core.ai.provider.ProviderSpendGuard;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.team.TeamQuotas;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGatewayMetricsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LangChain4jChat langChain4jChat;

    @Mock
    RouteResolution routeResolution;

    @Mock
    AiSettings aiSettings;

    @Mock
    GatewaySessions gatewaySessions;

    @Mock
    Conversations conversations;

    @Mock
    AiAgentAdapterRegistry agentAdapterRegistry;

    @Mock
    AiMemoryContextBuilder memoryContextBuilder;

    @Mock
    PlanResolution planResolution;

    @Mock
    PlanQuotas planQuotas;

    @Mock
    AiChatRequestRepository chatRequestRepository;

    @Mock
    MemoryTaskScheduler memoryTaskScheduler;

    @Mock
    CircuitBreaker circuitBreaker;

    @Mock
    ProviderSpendGuard providerSpendGuard;

    @Mock
    TeamQuotas teamQuotas;

    @Mock
    GatewayKeys gatewayKeys;

    private SimpleMeterRegistry registry;

    private AiGateway service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        var properties = new AiProperties();
        properties.getGateway().setInjectionEnabled(false);
        lenient().when(gatewayKeys.authorize(any(), any()))
                .thenReturn(Mono.just(GatewayKeys.Access.unrestricted()));
        lenient().when(gatewayKeys.acquire(any()))
                .thenReturn(Mono.just(AiGatewayRateLimiter.Permit.empty()));
        lenient().when(gatewayKeys.release(any())).thenReturn(Mono.empty());
        lenient().when(providerSpendGuard.check(any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(teamQuotas.check(any(), any())).thenReturn(Mono.empty());
        lenient().when(planResolution.resolve("user-1")).thenReturn(Mono.just(resolved()));
        lenient()
                .when(planQuotas.reserve(anyString(), anyString(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(Mono.just(reserveResult()));
        lenient().when(planQuotas.settle(anyString(), anyLong(), any())).thenReturn(Mono.just(settleResult()));
        lenient().when(planQuotas.release(anyString())).thenReturn(Mono.just(releaseResult()));
        service = new AiGateway(
                client,
                langChain4jChat,
                routeResolution,
                aiSettings,
                gatewaySessions,
                conversations,
                agentAdapterRegistry,
                memoryContextBuilder,
                planResolution,
                planQuotas,
                chatRequestRepository,
                memoryTaskScheduler,
                circuitBreaker,
                new AiGatewayRateLimiter(null),
                gatewayKeys,
                new SunkRouteRegistry(),
                properties,
                new AiMetrics(registry),
                providerSpendGuard,
                teamQuotas);
    }

    @Test
    void recordsSuccessMetricsAfterACompletedChat() {
        stubPreparedGateway();
                when(langChain4jChat.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any())).thenReturn(Mono.just(response()));
        when(chatRequestRepository.completeChatRequest(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.complete(invocation())).expectNextCount(1).verifyComplete();

        assertThat(requestCount("OPENAI_CHAT", "pudans-chat", "SUCCESS")).isEqualTo(1.0);
        assertThat(latencyCount("OPENAI_CHAT", "SUCCESS")).isEqualTo(1);
        assertThat(tokenCount("input")).isEqualTo(1000.0);
        assertThat(tokenCount("output")).isEqualTo(1000.0);
        assertThat(registry.find("ai.gateway.tokens").tag("direction", "cache").counter()).isNull();
        assertThat(unitsCount()).isEqualTo(2500.0);
        assertThat(routingCount("group-a", "SMART", "SELECTED")).isEqualTo(1.0);
        assertThat(registry.find("ai.alerts").counter()).isNull();
    }

    @Test
    void recordsFailureMetricsAndProviderExhaustedAlert() {
        stubPreparedGateway();
                when(langChain4jChat.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.error(new IllegalStateException("upstream down")));
        when(chatRequestRepository.completeChatRequest(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.complete(invocation())).expectError(ApiException.class).verify();

        assertThat(requestCount("OPENAI_CHAT", "pudans-chat", "FAILED")).isEqualTo(1.0);
        assertThat(latencyCount("OPENAI_CHAT", "FAILED")).isEqualTo(1);
        assertThat(alertCount("provider_exhausted", "WARN")).isEqualTo(1.0);
        assertThat(registry.find("ai.gateway.tokens").counter()).isNull();
        assertThat(registry.find("ai.gateway.units").counter()).isNull();
    }

    @Test
    void recordsRouteExhaustedAlertWhenResolutionHasNoSelection() {
        when(aiSettings.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(gatewaySessions.bind(any())).thenReturn(Mono.just(session()));
        when(routeResolution.resolve(any())).thenReturn(Mono.error(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "ai-route-no-candidate", "路由组没有可用候选。")));

        StepVerifier.create(service.complete(invocation())).expectError(ApiException.class).verify();

        assertThat(alertCount("route_exhausted", "WARN")).isEqualTo(1.0);
        assertThat(registry.find("ai.gateway.requests").counter()).isNull();
        assertThat(registry.find("ai.routing.decisions").counter()).isNull();
    }

    private void stubPreparedGateway() {
        stubResolvedResources();
        when(chatRequestRepository.createChatRequest(any())).thenReturn(Mono.empty());
    }

    private void stubResolvedResources() {
        when(aiSettings.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(gatewaySessions.bind(any())).thenReturn(Mono.just(session()));
        when(routeResolution.resolve(any())).thenReturn(Mono.just(resolution()));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider()));
        when(client.get(AiProviderAccount.class, "account-a")).thenReturn(Mono.just(account()));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model()));
        lenient().when(conversations.requireOwned(11L, "user-1"))
                .thenReturn(Mono.just(conversation()));
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

    private static RouteResolution.Resolution resolution() {
        var group = new AiRouteGroup();
        group.setMetadata(metadata("group-a"));
        var selection = new RouteSelection("provider-a", "account-a", "model-a", AiModel.Tier.CHEAP);
        var plan = new RoutePlan(
                selection,
                List.of(),
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.SMART,
                        null,
                        AiModel.Tier.CHEAP,
                        null,
                        null,
                        List.of(),
                        selection,
                        RouteOutcome.SELECTED,
                        List.of()));
        var request = RouteRequest.builder().promptTokens(1000).maxOutputTokens(1000).build();
        return new RouteResolution.Resolution(group, null, request, plan, List.of());
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("provider-a");
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_CHAT);
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        return account;
    }

    private static AiModel model() {
        var model = new AiModel();
        model.setMetadata(metadata("model-a"));
        model.getSpec().setProvider("provider-a");
        model.getSpec().setModelCode("model-a");
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
                "model-a",
                new AiChatMessage("assistant", List.of(new AiContentPart.Text("pong", null))),
                new AiTokenUsage(1000, 1000, null, null),
                "stop");
    }

    private static PlanQuotas.ReserveResult reserveResult() {
        return new PlanQuotas.ReserveResult(
                "req-1", "user-1", "standard", 2500, "five-hour-1", "weekly-1", false);
    }

    private static PlanQuotas.SettleResult settleResult() {
        return new PlanQuotas.SettleResult("req-1", 2500, false);
    }

    private static PlanQuotas.ReleaseResult releaseResult() {
        return new PlanQuotas.ReleaseResult("req-1", true);
    }

    private static PlanResolution.ResolvedPlan resolved() {
        var plan = new AiPlan();
        plan.setMetadata(metadata("standard"));
        plan.getSpec().setDisplayName("标准套餐");
        plan.getSpec().getQuota().getFiveHour().setLimit(3_000_000);
        plan.getSpec().getQuota().getWeekly().setLimit(20_000_000);
        return new PlanResolution.ResolvedPlan(
                "standard", "标准套餐", "User/user-1", plan);
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }

    private double requestCount(String protocol, String agentSource, String status) {
        var counter = registry.find("ai.gateway.requests")
                .tag("protocol", protocol)
                .tag("agent_source", agentSource)
                .tag("status", status)
                .counter();
        assertThat(counter).isNotNull();
        return counter.count();
    }

    private long latencyCount(String protocol, String status) {
        var timer = registry.find("ai.gateway.latency")
                .tag("protocol", protocol)
                .tag("status", status)
                .timer();
        assertThat(timer).isNotNull();
        return timer.count();
    }

    private double tokenCount(String direction) {
        var counter = registry.find("ai.gateway.tokens").tag("direction", direction).counter();
        assertThat(counter).isNotNull();
        return counter.count();
    }

    private double unitsCount() {
        var counter = registry.find("ai.gateway.units").counter();
        assertThat(counter).isNotNull();
        return counter.count();
    }

    private double routingCount(String group, String mode, String outcome) {
        var counter = registry.find("ai.routing.decisions")
                .tag("route_group", group)
                .tag("route_mode", mode)
                .tag("outcome", outcome)
                .counter();
        assertThat(counter).isNotNull();
        return counter.count();
    }

    private double alertCount(String type, String severity) {
        var counter = registry.find("ai.alerts")
                .tag("type", type)
                .tag("severity", severity)
                .counter();
        assertThat(counter).isNotNull();
        return counter.count();
    }
}
