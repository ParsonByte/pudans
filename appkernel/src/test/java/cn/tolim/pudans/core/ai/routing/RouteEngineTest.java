package cn.tolim.pudans.core.ai.routing;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreakerConfig;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitFailure;
import cn.tolim.pudans.core.ai.routing.circuit.InMemoryCircuitBreaker;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityLevel;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityVerdict;
import cn.tolim.pudans.core.ai.routing.classifier.HeuristicComplexityClassifier;
import cn.tolim.pudans.core.ai.routing.strategy.RouteStrategyRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouteEngineTest {

    private static final CircuitBreakerConfig DEFAULT_BREAKER_CONFIG =
            new CircuitBreakerConfig(5, 3, Duration.ofSeconds(30), Duration.ofMinutes(10), 30_000);

    private InMemoryCircuitBreaker breaker;

    private RouteEngine engine;

    @BeforeEach
    void setUp() {
        breaker = new InMemoryCircuitBreaker(DEFAULT_BREAKER_CONFIG);
        engine = new RouteEngine(new RouteStrategyRegistry(), breaker, new HeuristicComplexityClassifier());
    }

    @Test
    void filtersIneligibleSeatsAndRecordsReasons() {
        var disabled = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m-disabled")
                .tier(AiModel.Tier.HIGH)
                .enabled(false)
                .capabilities(Set.of(AiModel.Capability.CHAT, AiModel.Capability.TOOLS))
                .build();
        var resting = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m-resting")
                .tier(AiModel.Tier.HIGH)
                .resting(true)
                .capabilities(Set.of(AiModel.Capability.CHAT, AiModel.Capability.TOOLS))
                .build();
        var unhealthy = RouteSeat.builder()
                .provider("p")
                .account("a3")
                .model("m-unhealthy")
                .tier(AiModel.Tier.HIGH)
                .healthScore(40)
                .capabilities(Set.of(AiModel.Capability.CHAT, AiModel.Capability.TOOLS))
                .build();
        var noTools = RouteSeat.builder()
                .provider("p")
                .account("a4")
                .model("m-no-tools")
                .tier(AiModel.Tier.HIGH)
                .capabilities(Set.of(AiModel.Capability.CHAT))
                .build();
        var tightContext = RouteSeat.builder()
                .provider("p")
                .account("a5")
                .model("m-tight")
                .tier(AiModel.Tier.HIGH)
                .capabilities(Set.of(AiModel.Capability.CHAT, AiModel.Capability.TOOLS))
                .contextWindow(1000)
                .extraContextMargin(0)
                .build();
        var spent = RouteSeat.builder()
                .provider("p")
                .account("a6")
                .model("m-spent")
                .tier(AiModel.Tier.HIGH)
                .spent(true)
                .capabilities(Set.of(AiModel.Capability.CHAT, AiModel.Capability.TOOLS))
                .build();
        var healthy = RouteSeat.builder()
                .provider("p")
                .account("a7")
                .model("m-ok")
                .tier(AiModel.Tier.HIGH)
                .capabilities(Set.of(AiModel.Capability.CHAT, AiModel.Capability.TOOLS))
                .build();
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .requiredCapabilities(Set.of(AiModel.Capability.TOOLS))
                .promptTokens(900)
                .maxOutputTokens(200)
                .build();

        var plan = engine.route(
                request, List.of(disabled, resting, unhealthy, noTools, tightContext, spent, healthy));

        assertThat(plan.routed()).isTrue();
        assertThat(plan.selected().model()).isEqualTo("m-ok");
        assertThat(plan.trace().candidates())
                .extracting(RouteTrace.Candidate::model)
                .containsExactly("m-disabled", "m-resting", "m-unhealthy", "m-no-tools", "m-tight", "m-spent", "m-ok");
        assertThat(plan.trace().candidates())
                .extracting(RouteTrace.Candidate::eligible)
                .containsExactly(false, false, false, false, false, false, true);
        assertThat(plan.trace().candidates())
                .extracting(RouteTrace.Candidate::reason)
                .containsExactly(
                        RouteEliminationReason.DISABLED,
                        RouteEliminationReason.RESTING,
                        RouteEliminationReason.UNHEALTHY,
                        RouteEliminationReason.CAPABILITY_MISMATCH,
                        RouteEliminationReason.CONTEXT_EXCEEDED,
                        RouteEliminationReason.SPENT,
                        null);
    }

    @Test
    void filtersSeatsWhoseProviderOrAccountCircuitIsOpen() {
        for (int index = 0; index < DEFAULT_BREAKER_CONFIG.failureThreshold(); index++) {
            breaker.recordFailure("p-open", CircuitFailure.defaultFailure());
            breaker.recordFailure("a-open", CircuitFailure.defaultFailure());
        }
        var providerSeat = RouteSeats.seat("p-open", "a1", "m1", AiModel.Tier.HIGH);
        var accountSeat = RouteSeats.seat("p2", "a-open", "m2", AiModel.Tier.HIGH);

        var plan = engine.route(
                RouteRequest.builder().routeMode(AiRouteGroup.RouteMode.ORDER).build(),
                List.of(providerSeat, accountSeat));

        assertThat(plan.routed()).isFalse();
        assertThat(plan.trace().candidates())
                .extracting(RouteTrace.Candidate::reason)
                .containsExactly(RouteEliminationReason.RESTING, RouteEliminationReason.RESTING);
    }

    @Test
    void keepsASeatEligibleAfterOneFailureWhileTheCircuitIsClosed() {
        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        breaker.recordFailure("a", CircuitFailure.defaultFailure());
        var seat = RouteSeats.seat("p", "a", "m1", AiModel.Tier.HIGH);

        var plan = engine.route(
                RouteRequest.builder().routeMode(AiRouteGroup.RouteMode.ORDER).build(), List.of(seat));

        assertThat(plan.routed()).isTrue();
        assertThat(plan.selected().model()).isEqualTo("m1");
    }

    @Test
    void resolvesExplicitTierBeforeDefaults() {
        var request = RouteRequest.builder()
                .requestedTier(AiModel.Tier.ULTRA)
                .chatModeDefaultTier(AiModel.Tier.MEDIUM)
                .routeGroupDefaultTier(AiModel.Tier.CHEAP)
                .build();

        var plan = engine.route(request, List.of(RouteSeats.seat("p", "a1", "m1", AiModel.Tier.ULTRA)));

        assertThat(plan.trace().resolvedTier()).isEqualTo(AiModel.Tier.ULTRA);
        assertThat(plan.trace().classification()).isNull();
    }

    @Test
    void resolvesChatModeTierBeforeRouteGroupWhenRequestIsAuto() {
        var request = RouteRequest.builder()
                .requestedTier(AiModel.Tier.AUTO)
                .chatModeDefaultTier(AiModel.Tier.MEDIUM)
                .routeGroupDefaultTier(AiModel.Tier.CHEAP)
                .build();

        var plan = engine.route(request, List.of(RouteSeats.seat("p", "a1", "m1", AiModel.Tier.MEDIUM)));

        assertThat(plan.trace().resolvedTier()).isEqualTo(AiModel.Tier.MEDIUM);
        assertThat(plan.trace().classification()).isNull();
    }

    @Test
    void resolvesRouteGroupTierWhenOtherTiersAreAuto() {
        var request = RouteRequest.builder()
                .requestedTier(AiModel.Tier.AUTO)
                .chatModeDefaultTier(AiModel.Tier.AUTO)
                .routeGroupDefaultTier(AiModel.Tier.CHEAP)
                .build();

        var plan = engine.route(request, List.of(RouteSeats.seat("p", "a1", "m1", AiModel.Tier.CHEAP)));

        assertThat(plan.trace().resolvedTier()).isEqualTo(AiModel.Tier.CHEAP);
        assertThat(plan.trace().classification()).isNull();
    }

    @Test
    void autoTierUsesHeuristicClassifier() {
        var request = RouteRequest.builder().promptTokens(3000).build();

        var plan = engine.route(request, List.of(RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH)));

        assertThat(plan.trace().resolvedTier()).isEqualTo(AiModel.Tier.HIGH);
        assertThat(plan.trace().classification().level()).isEqualTo(ComplexityLevel.COMPLEX);
        assertThat(plan.trace().classification().confidence()).isEqualTo(0.85);
    }

    @Test
    void lowConfidenceResultUpgradesToFallbackTier() {
        var lowConfidenceEngine = new RouteEngine(
                new RouteStrategyRegistry(),
                breaker,
                input -> new ComplexityVerdict(ComplexityLevel.SIMPLE, 0.2));
        var request = RouteRequest.builder()
                .qualityThreshold(0.6)
                .classifierFallbackTier(AiModel.Tier.HIGH)
                .build();

        var plan = lowConfidenceEngine.route(
                request, List.of(RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH)));

        assertThat(plan.trace().resolvedTier()).isEqualTo(AiModel.Tier.HIGH);
        assertThat(plan.trace().classification().level()).isEqualTo(ComplexityLevel.SIMPLE);
        assertThat(plan.trace().classification().confidence()).isEqualTo(0.2);
    }

    @Test
    void classifierFailureFallsBackToConfiguredTier() {
        var failingEngine = new RouteEngine(new RouteStrategyRegistry(), breaker, input -> {
            throw new IllegalStateException("分类器不可用");
        });
        var request = RouteRequest.builder().classifierFallbackTier(AiModel.Tier.MEDIUM).build();

        var plan = failingEngine.route(request, List.of(RouteSeats.seat("p", "a1", "m1", AiModel.Tier.MEDIUM)));

        assertThat(plan.trace().resolvedTier()).isEqualTo(AiModel.Tier.MEDIUM);
        assertThat(plan.trace().classification()).isNull();
    }

    @Test
    void classifierDisabledUsesFallbackTier() {
        var request = RouteRequest.builder()
                .classifierEnabled(false)
                .classifierFallbackTier(AiModel.Tier.MEDIUM)
                .build();

        var plan = engine.route(request, List.of(RouteSeats.seat("p", "a1", "m1", AiModel.Tier.MEDIUM)));

        assertThat(plan.trace().resolvedTier()).isEqualTo(AiModel.Tier.MEDIUM);
        assertThat(plan.trace().classification()).isNull();
    }

    @Test
    void prefersSeatsMatchingResolvedTier() {
        var cheap = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.CHEAP);
        var high = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .chatModeDefaultTier(AiModel.Tier.HIGH)
                .build();

        var plan = engine.route(request, List.of(cheap, high));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(high));
    }

    @Test
    void autoAffinityKeepsPreviousSelectionWhenCacheIsWarm() {
        var first = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .affinity(AiProvider.Affinity.AUTO)
                .previousSelection(RouteSelection.of(second))
                .affinityCacheWorthKeeping(true)
                .build();

        var plan = engine.route(request, List.of(first, second));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(second));
    }

    @Test
    void autoAffinityReselectsWhenCacheIsCold() {
        var first = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .affinity(AiProvider.Affinity.AUTO)
                .previousSelection(RouteSelection.of(second))
                .affinityCacheWorthKeeping(false)
                .build();

        var plan = engine.route(request, List.of(first, second));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(first));
    }

    @Test
    void sessionAndTurnAffinityPreferPreviousSelection() {
        var first = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);

        for (var affinity : List.of(AiProvider.Affinity.SESSION, AiProvider.Affinity.TURN)) {
            var request = RouteRequest.builder()
                    .routeMode(AiRouteGroup.RouteMode.ORDER)
                    .affinity(affinity)
                    .previousSelection(RouteSelection.of(second))
                    .build();

            var plan = engine.route(request, List.of(first, second));

            assertThat(plan.selected()).isEqualTo(RouteSelection.of(second));
        }
    }

    @Test
    void offAffinityIgnoresPreviousSelection() {
        var first = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .affinity(AiProvider.Affinity.OFF)
                .previousSelection(RouteSelection.of(second))
                .build();

        var plan = engine.route(request, List.of(first, second));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(first));
    }

    @Test
    void strictAffinityKeepsPinnedAccountWithoutFallback() {
        var pinned = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH);
        var other = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .affinity(AiProvider.Affinity.STRICT)
                .previousSelection(RouteSelection.of(pinned))
                .fallbackReferences(List.of("p/other"))
                .build();

        var plan = engine.route(request, List.of(other, pinned));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(pinned));
        assertThat(plan.fallbackSeats()).isEmpty();
        assertThat(plan.fallbackReferences()).isEmpty();
        assertThat(plan.trace().fallback()).isEmpty();
        assertThat(plan.trace().outcome()).isEqualTo(RouteOutcome.SELECTED);
    }

    @Test
    void strictAffinityReturnsErrorWhenPinnedAccountIsUnavailable() {
        var disabled = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.HIGH)
                .enabled(false)
                .build();
        var other = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .affinity(AiProvider.Affinity.STRICT)
                .previousSelection(new RouteSelection("p", "a1", "m1", AiModel.Tier.HIGH))
                .build();

        var plan = engine.route(request, List.of(disabled, other));

        assertThat(plan.routed()).isFalse();
        assertThat(plan.trace().outcome()).isEqualTo(RouteOutcome.STRICT_PINNED_UNAVAILABLE);
        assertThat(plan.fallbackSeats()).isEmpty();
        assertThat(plan.fallbackReferences()).isEmpty();
        assertThat(plan.trace().fallback()).isEmpty();
    }

    @Test
    void fallbackOrderPutsConfiguredReferencesBeforeRemainingSeats() {
        var first = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var third = RouteSeats.seat("p", "a3", "m3", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .requestedTier(AiModel.Tier.HIGH)
                .fallbackReferences(List.of("p/backup", "group/fallback"))
                .build();

        var plan = engine.route(request, List.of(first, second, third));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(first));
        assertThat(plan.fallbackSeats()).containsExactly(RouteSelection.of(second), RouteSelection.of(third));
        assertThat(plan.fallbackReferences()).containsExactly("p/backup", "group/fallback");
        assertThat(plan.trace().fallback())
                .containsExactly("p/backup", "group/fallback", "p/m2", "p/m3");
    }

    @Test
    void contextModePrefersTheSmallestWindow() {
        var large = RouteSeats.seat("p", "a1", "m-large", AiModel.Tier.HIGH);
        var small = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m-small")
                .tier(AiModel.Tier.HIGH)
                .contextWindow(8_000)
                .build();
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .contextMode("SMALLEST")
                .build();

        var plan = engine.route(request, List.of(large, small));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(small));
    }

    @Test
    void contextModePrefersTheLargestWindow() {
        var large = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m-large")
                .tier(AiModel.Tier.HIGH)
                .contextWindow(200_000)
                .build();
        var small = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m-small")
                .tier(AiModel.Tier.HIGH)
                .contextWindow(8_000)
                .build();
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .contextMode("LARGEST")
                .build();

        var plan = engine.route(request, List.of(small, large));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(large));
    }

    @Test
    void ruleOnRequestedModelPinsItsMemberAndHonorsPriority() {
        var other = RouteSeats.seat("p", "a1", "m-other", AiModel.Tier.HIGH);
        var low = RouteSeats.seat("p", "a2", "m-low", AiModel.Tier.HIGH);
        var high = RouteSeats.seat("p", "a3", "m-high", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .requestedModel("gpt-test")
                .rules(List.of(
                        rule("model:gpt-test", "m-low", 1),
                        rule("model:gpt-test", "m-high", 5)))
                .build();

        var plan = engine.route(request, List.of(other, low, high));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(high));
    }

    @Test
    void ruleConditionRequiresEveryCapabilityAndContextClause() {
        var pinned = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m-pinned")
                .tier(AiModel.Tier.HIGH)
                .capabilities(Set.of(AiModel.Capability.TOOLS))
                .build();
        var other = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m-other")
                .tier(AiModel.Tier.HIGH)
                .capabilities(Set.of(AiModel.Capability.TOOLS))
                .build();
        var matching = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .promptTokens(1200)
                .requiredCapabilities(Set.of(AiModel.Capability.TOOLS))
                .rules(List.of(rule("capability:TOOLS,context>=1000", "m-pinned", 1)))
                .build();
        var shortContext = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .promptTokens(999)
                .requiredCapabilities(Set.of(AiModel.Capability.TOOLS))
                .rules(List.of(rule("capability:TOOLS,context>=1000", "m-pinned", 1)))
                .build();
        var missingCapability = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .promptTokens(1200)
                .rules(List.of(rule("capability:TOOLS,context>=1000", "m-pinned", 1)))
                .build();

        assertThat(engine.route(matching, List.of(other, pinned)).selected())
                .isEqualTo(RouteSelection.of(pinned));
        assertThat(engine.route(shortContext, List.of(other, pinned)).selected())
                .isEqualTo(RouteSelection.of(other));
        assertThat(engine.route(missingCapability, List.of(other, pinned)).selected())
                .isEqualTo(RouteSelection.of(other));
    }

    @Test
    void ruleProviderConditionMatchesTheRequestedModelProvider() {
        var first = RouteSeats.seat("p1", "a1", "m1", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p2", "a2", "m2", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .requestedModel("m2")
                .rules(List.of(rule("provider:p2", "m2", 1)))
                .build();

        var plan = engine.route(request, List.of(first, second));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(second));
    }

    @Test
    void sinkGroupsExcludeRecentlyRateLimitedSeats() {
        var sunk = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m-sunk")
                .tier(AiModel.Tier.HIGH)
                .sunk(true)
                .build();
        var healthy = RouteSeats.seat("p", "a2", "m-ok", AiModel.Tier.HIGH);
        var sinkRequest = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .sink(true)
                .build();
        var plainRequest = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .build();

        var sinkPlan = engine.route(sinkRequest, List.of(sunk, healthy));
        var plainPlan = engine.route(plainRequest, List.of(sunk, healthy));

        assertThat(sinkPlan.selected()).isEqualTo(RouteSelection.of(healthy));
        assertThat(sinkPlan.trace().candidates().getFirst().reason()).isEqualTo(RouteEliminationReason.SUNK);
        assertThat(plainPlan.selected()).isEqualTo(RouteSelection.of(sunk));
    }

    @Test
    void filtersSeatsWhenTheClientRequestsAnExplicitModel() {
        var first = RouteSeats.seat("p", "a1", "m-first", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m-second", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .explicitModel("m-second")
                .build();

        var plan = engine.route(request, List.of(first, second));

        assertThat(plan.routed()).isTrue();
        assertThat(plan.selected().model()).isEqualTo("m-second");
    }

    @Test
    void reportsNoRouteWhenTheRequestedModelIsNotAmongCandidates() {
        var seat = RouteSeats.seat("p", "a1", "m-first", AiModel.Tier.HIGH);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .explicitModel("m-missing")
                .build();

        var plan = engine.route(request, List.of(seat));

        assertThat(plan.routed()).isFalse();
    }

    @Test
    void recordsTheMatchedRuleInTheTrace() {
        var pinned = RouteSeats.seat("p", "a1", "m-pinned", AiModel.Tier.HIGH);
        var other = RouteSeats.seat("p", "a2", "m-other", AiModel.Tier.HIGH);
        var rule = new AiRouteGroup.Rule();
        rule.setName("vision");
        rule.setCondition("context>=0");
        rule.setMember("m-pinned");
        rule.setPriority(5);
        rule.setAgents(List.of("agent-a"));
        rule.setTokensGte(100);
        rule.setImagesGte(2);
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .agentName("agent-a")
                .promptTokens(150)
                .imageCount(2)
                .rules(List.of(rule))
                .build();

        var plan = engine.route(request, List.of(other, pinned));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(pinned));
        assertThat(plan.trace().matchedRule()).isNotNull();
        assertThat(plan.trace().matchedRule().name()).isEqualTo("vision");
        assertThat(plan.trace().matchedRule().member()).isEqualTo("m-pinned");
        assertThat(plan.trace().matchedRule().priority()).isEqualTo(5);
    }

    @Test
    void ignoresPausedRulesAndStructuredFieldsThatDoNotMatch() {
        var first = RouteSeats.seat("p", "a1", "m-first", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m-second", AiModel.Tier.HIGH);
        var paused = rule("capability:CHAT", "m-second", 1);
        paused.setPause(true);
        var missingAgent = rule("capability:CHAT", "m-second", 2);
        missingAgent.setAgents(List.of("agent-missing"));
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .agentName("agent-a")
                .rules(List.of(paused, missingAgent))
                .build();

        var plan = engine.route(request, List.of(first, second));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(first));
        assertThat(plan.trace().matchedRule()).isNull();
    }

    @Test
    void useOverridesTheRuleMember() {
        var pinned = RouteSeats.seat("p", "a1", "m-pinned", AiModel.Tier.HIGH);
        var other = RouteSeats.seat("p", "a2", "m-other", AiModel.Tier.HIGH);
        var rule = rule("context>=0", "m-other", 1);
        rule.setUse("m-pinned");
        var request = RouteRequest.builder()
                .routeMode(AiRouteGroup.RouteMode.ORDER)
                .rules(List.of(rule))
                .build();

        var plan = engine.route(request, List.of(other, pinned));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(pinned));
        assertThat(plan.trace().matchedRule().member()).isEqualTo("m-pinned");
    }

    @Test
    void ordersCandidatesByProviderOrderBeforeTheStrategy() {
        var late = RouteSeat.builder()
                .provider("p-late")
                .account("a-late")
                .model("m-late")
                .tier(AiModel.Tier.HIGH)
                .providerOrder(2)
                .build();
        var early = RouteSeat.builder()
                .provider("p-early")
                .account("a-early")
                .model("m-early")
                .tier(AiModel.Tier.HIGH)
                .providerOrder(1)
                .build();
        var request = RouteRequest.builder().routeMode(AiRouteGroup.RouteMode.ORDER).build();

        var plan = engine.route(request, List.of(late, early));

        assertThat(plan.selected()).isEqualTo(RouteSelection.of(early));
    }

    private static AiRouteGroup.Rule rule(String condition, String member, int priority) {
        var rule = new AiRouteGroup.Rule();
        rule.setCondition(condition);
        rule.setMember(member);
        rule.setPriority(priority);
        return rule;
    }

    @Test
    void emptyCandidatesReturnExhausted() {
        var plan = engine.route(
                RouteRequest.builder().fallbackReferences(List.of("p/backup")).build(), List.of());

        assertThat(plan.routed()).isFalse();
        assertThat(plan.trace().outcome()).isEqualTo(RouteOutcome.EXHAUSTED);
        assertThat(plan.fallbackSeats()).isEmpty();
        assertThat(plan.trace().fallback()).containsExactly("p/backup");
    }
}
