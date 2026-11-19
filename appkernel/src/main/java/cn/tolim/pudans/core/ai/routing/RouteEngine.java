package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityClassifier;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityInput;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityLevel;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityVerdict;
import cn.tolim.pudans.core.ai.routing.strategy.RouteStrategyContext;
import cn.tolim.pudans.core.ai.routing.strategy.RouteStrategyRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RouteEngine {

    private static final double DEFAULT_QUALITY_THRESHOLD = 0.6;

    private static final AiModel.Tier DEFAULT_FALLBACK_TIER = AiModel.Tier.HIGH;

    private final RouteStrategyRegistry strategies;

    private final CircuitBreaker circuitBreaker;

    private final ComplexityClassifier classifier;

    public RouteEngine(
            RouteStrategyRegistry strategies, CircuitBreaker circuitBreaker, ComplexityClassifier classifier) {
        this.strategies = Objects.requireNonNull(strategies, "strategies");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
    }

    public RoutePlan route(RouteRequest request, List<RouteSeat> candidates) {
        Objects.requireNonNull(request, "request");
        var seats = candidates == null ? List.<RouteSeat>of() : candidates;
        var tier = resolveTier(request);
        var traceCandidates = new ArrayList<RouteTrace.Candidate>();
        var eligible = new ArrayList<RouteSeat>();
        for (var seat : seats) {
            var reason = eliminationReason(seat, request);
            traceCandidates.add(new RouteTrace.Candidate(
                    seat.getProvider(),
                    seat.getAccount(),
                    seat.getModel(),
                    seat.getTier(),
                    reason == null,
                    reason,
                    effectiveHealth(seat)));
            if (reason == null) {
                eligible.add(seat);
            }
        }
        var strategy = strategies.strategyFor(routeModeOf(request));
        var ordered = strategy.sort(eligible, new RouteStrategyContext(request.getManualPick()));
        ordered = preferResolvedTier(ordered, tier.tier());
        var affinity = affinityOf(request);
        if (affinity == AiProvider.Affinity.STRICT && request.getPreviousSelection() != null) {
            return strictPlan(request, tier, traceCandidates, ordered);
        }
        ordered = applyAffinity(request, ordered, affinity);
        if (ordered.isEmpty()) {
            return new RoutePlan(
                    null,
                    List.of(),
                    request.getFallbackReferences(),
                    trace(
                            request,
                            tier,
                            traceCandidates,
                            null,
                            RouteOutcome.EXHAUSTED,
                            request.getFallbackReferences()));
        }
        var selected = ordered.get(0);
        var fallbackSeats = ordered.stream().skip(1).map(RouteSelection::of).toList();
        var fallback = fallbackReferences(fallbackSeats, request.getFallbackReferences());
        return new RoutePlan(
                RouteSelection.of(selected),
                fallbackSeats,
                request.getFallbackReferences(),
                trace(
                        request,
                        tier,
                        traceCandidates,
                        RouteSelection.of(selected),
                        RouteOutcome.SELECTED,
                        fallback));
    }

    private RoutePlan strictPlan(
            RouteRequest request,
            TierResolution tier,
            List<RouteTrace.Candidate> traceCandidates,
            List<RouteSeat> ordered) {
        var previous = request.getPreviousSelection();
        var pinned = ordered.stream().filter(seat -> matchesAccount(seat, previous)).toList();
        if (pinned.isEmpty()) {
            return new RoutePlan(
                    null,
                    List.of(),
                    List.of(),
                    trace(
                            request,
                            tier,
                            traceCandidates,
                            null,
                            RouteOutcome.STRICT_PINNED_UNAVAILABLE,
                            List.of()));
        }
        var exactModel = pinned.stream()
                .filter(seat -> Objects.equals(seat.getModel(), previous.model()))
                .findFirst();
        var selected = RouteSelection.of(exactModel.orElse(pinned.get(0)));
        return new RoutePlan(
                selected,
                List.of(),
                List.of(),
                trace(request, tier, traceCandidates, selected, RouteOutcome.SELECTED, List.of()));
    }

    private List<RouteSeat> applyAffinity(
            RouteRequest request, List<RouteSeat> ordered, AiProvider.Affinity affinity) {
        var previous = request.getPreviousSelection();
        if (previous == null) {
            return ordered;
        }
        return switch (affinity) {
            case OFF, STRICT -> ordered;
            case AUTO -> request.isAffinityCacheWorthKeeping()
                    ? pin(ordered, seat -> matchesSelection(seat, previous))
                    : ordered;
            case SESSION, TURN -> pin(ordered, seat -> matchesSelection(seat, previous));
        };
    }

    private static List<RouteSeat> pin(List<RouteSeat> seats, Predicate<RouteSeat> predicate) {
        var pinned = new ArrayList<RouteSeat>();
        var rest = new ArrayList<RouteSeat>();
        for (var seat : seats) {
            if (predicate.test(seat)) {
                pinned.add(seat);
            } else {
                rest.add(seat);
            }
        }
        if (pinned.isEmpty()) {
            return seats;
        }
        pinned.addAll(rest);
        return pinned;
    }

    private static boolean matchesSelection(RouteSeat seat, RouteSelection selection) {
        return matchesAccount(seat, selection) && Objects.equals(seat.getModel(), selection.model());
    }

    private static boolean matchesAccount(RouteSeat seat, RouteSelection selection) {
        return Objects.equals(seat.getProvider(), selection.provider())
                && Objects.equals(seat.getAccount(), selection.account());
    }

    private TierResolution resolveTier(RouteRequest request) {
        if (isConcreteTier(request.getRequestedTier())) {
            return new TierResolution(request.getRequestedTier(), null);
        }
        if (isConcreteTier(request.getChatModeDefaultTier())) {
            return new TierResolution(request.getChatModeDefaultTier(), null);
        }
        if (isConcreteTier(request.getRouteGroupDefaultTier())) {
            return new TierResolution(request.getRouteGroupDefaultTier(), null);
        }
        var fallbackTier = isConcreteTier(request.getClassifierFallbackTier())
                ? request.getClassifierFallbackTier()
                : DEFAULT_FALLBACK_TIER;
        if (!request.isClassifierEnabled()) {
            return new TierResolution(fallbackTier, null);
        }
        ComplexityVerdict verdict;
        try {
            verdict = classifier.classify(
                    new ComplexityInput(request.getLastUserMessage(), request.getPromptTokens()));
        } catch (RuntimeException ex) {
            log.warn("复杂度分类失败，按兜底档位处理", ex);
            return new TierResolution(fallbackTier, null);
        }
        var tier = tierFor(verdict.level());
        var threshold = request.getQualityThreshold() > 0
                ? request.getQualityThreshold()
                : DEFAULT_QUALITY_THRESHOLD;
        if (verdict.confidence() < threshold) {
            tier = maxTier(tier, fallbackTier);
        }
        return new TierResolution(
                tier, new RouteTrace.Classification(verdict.level(), verdict.confidence(), tier));
    }

    private static boolean isConcreteTier(AiModel.Tier tier) {
        return tier != null && tier != AiModel.Tier.AUTO;
    }

    private static AiModel.Tier tierFor(ComplexityLevel level) {
        return switch (level) {
            case SIMPLE -> AiModel.Tier.CHEAP;
            case MEDIUM -> AiModel.Tier.MEDIUM;
            case COMPLEX -> AiModel.Tier.HIGH;
        };
    }

    private static AiModel.Tier maxTier(AiModel.Tier left, AiModel.Tier right) {
        return rank(left) >= rank(right) ? left : right;
    }

    private static int rank(AiModel.Tier tier) {
        return switch (tier) {
            case AUTO -> 0;
            case CHEAP -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
            case ULTRA -> 3;
        };
    }

    private RouteEliminationReason eliminationReason(RouteSeat seat, RouteRequest request) {
        if (!seat.isEnabled()) {
            return RouteEliminationReason.DISABLED;
        }
        if (isResting(seat)) {
            return RouteEliminationReason.RESTING;
        }
        if (seat.getHealthScore() < request.getHealthThreshold()) {
            return RouteEliminationReason.UNHEALTHY;
        }
        if (!supports(seat, request.getRequiredCapabilities())
                || !supportsModalities(seat.getInputModalities(), request.getRequiredInputModalities())
                || !supportsModalities(seat.getOutputModalities(), request.getRequiredOutputModalities())) {
            return RouteEliminationReason.CAPABILITY_MISMATCH;
        }
        if (exceedsContext(seat, request)) {
            return RouteEliminationReason.CONTEXT_EXCEEDED;
        }
        if (seat.isSpent()) {
            return RouteEliminationReason.SPENT;
        }
        return null;
    }

    private boolean isResting(RouteSeat seat) {
        return seat.isResting()
                || (seat.getProvider() != null && circuitBreaker.isOpen(seat.getProvider()))
                || (seat.getAccount() != null && circuitBreaker.isOpen(seat.getAccount()));
    }

    private double effectiveHealth(RouteSeat seat) {
        var health = seat.getHealthScore();
        if (seat.getProvider() != null) {
            health = Math.min(health, circuitBreaker.healthScore(seat.getProvider()));
        }
        if (seat.getAccount() != null) {
            health = Math.min(health, circuitBreaker.healthScore(seat.getAccount()));
        }
        return health;
    }

    private static boolean supports(RouteSeat seat, Set<AiModel.Capability> required) {
        return required == null
                || required.isEmpty()
                || (seat.getCapabilities() != null && seat.getCapabilities().containsAll(required));
    }

    private static boolean supportsModalities(
            Set<AiModel.Modality> supported, Set<AiModel.Modality> required) {
        return required == null
                || required.isEmpty()
                || (supported != null && supported.containsAll(required));
    }

    private static boolean exceedsContext(RouteSeat seat, RouteRequest request) {
        if (seat.getContextWindow() <= 0
                || (request.getPromptTokens() <= 0 && request.getMaxOutputTokens() <= 0)) {
            return false;
        }
        long output = request.getMaxOutputTokens() > 0 ? request.getMaxOutputTokens() : seat.getMaxOutputTokens();
        long required = (long) request.getPromptTokens() + output + seat.getExtraContextMargin();
        return required > seat.getContextWindow();
    }

    private static List<RouteSeat> preferResolvedTier(List<RouteSeat> seats, AiModel.Tier tier) {
        var preferred = new ArrayList<RouteSeat>();
        var others = new ArrayList<RouteSeat>();
        for (var seat : seats) {
            if (seat.getTier() == tier) {
                preferred.add(seat);
            } else {
                others.add(seat);
            }
        }
        preferred.addAll(others);
        return preferred;
    }

    private static List<String> fallbackReferences(List<RouteSelection> seats, List<String> configured) {
        var references = new ArrayList<String>();
        if (configured != null) {
            references.addAll(configured);
        }
        for (var seat : seats) {
            references.add(seat.reference());
        }
        return references;
    }

    private static RouteTrace trace(
            RouteRequest request,
            TierResolution tier,
            List<RouteTrace.Candidate> candidates,
            RouteSelection selected,
            RouteOutcome outcome,
            List<String> fallback) {
        return new RouteTrace(
                routeModeOf(request),
                request.getRequestedTier(),
                tier.tier(),
                affinityOf(request),
                tier.classification(),
                candidates,
                selected,
                outcome,
                fallback);
    }

    private static AiRouteGroup.RouteMode routeModeOf(RouteRequest request) {
        return request.getRouteMode() == null ? AiRouteGroup.RouteMode.SMART : request.getRouteMode();
    }

    private static AiProvider.Affinity affinityOf(RouteRequest request) {
        return request.getAffinity() == null ? AiProvider.Affinity.AUTO : request.getAffinity();
    }

    private record TierResolution(AiModel.Tier tier, RouteTrace.Classification classification) {

        private TierResolution {
            Objects.requireNonNull(tier, "tier");
        }
    }
}
