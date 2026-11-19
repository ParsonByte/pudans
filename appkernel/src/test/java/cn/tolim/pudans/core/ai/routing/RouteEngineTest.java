package cn.tolim.pudans.core.ai.routing;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
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
    void emptyCandidatesReturnExhausted() {
        var plan = engine.route(
                RouteRequest.builder().fallbackReferences(List.of("p/backup")).build(), List.of());

        assertThat(plan.routed()).isFalse();
        assertThat(plan.trace().outcome()).isEqualTo(RouteOutcome.EXHAUSTED);
        assertThat(plan.fallbackSeats()).isEmpty();
        assertThat(plan.trace().fallback()).containsExactly("p/backup");
    }
}
