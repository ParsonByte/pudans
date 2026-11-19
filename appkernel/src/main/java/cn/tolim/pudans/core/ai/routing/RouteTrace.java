package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityLevel;
import java.util.List;

public record RouteTrace(
        AiRouteGroup.RouteMode routeMode,
        AiModel.Tier requestedTier,
        AiModel.Tier resolvedTier,
        AiProvider.Affinity affinity,
        Classification classification,
        List<Candidate> candidates,
        RouteSelection selected,
        RouteOutcome outcome,
        List<String> fallback) {

    public RouteTrace {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        fallback = fallback == null ? List.of() : List.copyOf(fallback);
    }

    public record Candidate(
            String provider,
            String account,
            String model,
            AiModel.Tier tier,
            boolean eligible,
            RouteEliminationReason reason,
            double healthScore) {
    }

    public record Classification(ComplexityLevel level, double confidence, AiModel.Tier tier) {
    }
}
