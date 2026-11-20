package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityLevel;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record RouteTrace(
        AiRouteGroup.RouteMode routeMode,
        AiModel.Tier requestedTier,
        AiModel.Tier resolvedTier,
        AiProvider.Affinity affinity,
        Classification classification,
        List<Candidate> candidates,
        RouteSelection selected,
        RouteOutcome outcome,
        List<String> fallback,
        @Nullable MatchedRule matchedRule,
        List<Attempt> attempts,
        List<String> fallbackGroups,
        @Nullable String callerKey,
        @Nullable String purpose,
        @Nullable String via) {

    public RouteTrace {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        fallback = fallback == null ? List.of() : List.copyOf(fallback);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        fallbackGroups = fallbackGroups == null ? List.of() : List.copyOf(fallbackGroups);
    }

    public RouteTrace(
            AiRouteGroup.RouteMode routeMode,
            AiModel.Tier requestedTier,
            AiModel.Tier resolvedTier,
            AiProvider.Affinity affinity,
            Classification classification,
            List<Candidate> candidates,
            RouteSelection selected,
            RouteOutcome outcome,
            List<String> fallback) {
        this(
                routeMode,
                requestedTier,
                resolvedTier,
                affinity,
                classification,
                candidates,
                selected,
                outcome,
                fallback,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    public RouteTrace withMatchedRule(@Nullable MatchedRule rule) {
        return new RouteTrace(
                routeMode,
                requestedTier,
                resolvedTier,
                affinity,
                classification,
                candidates,
                selected,
                outcome,
                fallback,
                rule,
                attempts,
                fallbackGroups,
                callerKey,
                purpose,
                via);
    }

    public RouteTrace withAttempts(List<Attempt> value) {
        return new RouteTrace(
                routeMode,
                requestedTier,
                resolvedTier,
                affinity,
                classification,
                candidates,
                selected,
                outcome,
                fallback,
                matchedRule,
                value,
                fallbackGroups,
                callerKey,
                purpose,
                via);
    }

    public RouteTrace withFallbackGroups(List<String> value) {
        return new RouteTrace(
                routeMode,
                requestedTier,
                resolvedTier,
                affinity,
                classification,
                candidates,
                selected,
                outcome,
                fallback,
                matchedRule,
                attempts,
                value,
                callerKey,
                purpose,
                via);
    }

    public RouteTrace withCallerKey(@Nullable String value) {
        return new RouteTrace(
                routeMode,
                requestedTier,
                resolvedTier,
                affinity,
                classification,
                candidates,
                selected,
                outcome,
                fallback,
                matchedRule,
                attempts,
                fallbackGroups,
                value,
                purpose,
                via);
    }

    public RouteTrace withGatewayContext(@Nullable String purpose, @Nullable String via) {
        return new RouteTrace(
                routeMode,
                requestedTier,
                resolvedTier,
                affinity,
                classification,
                candidates,
                selected,
                outcome,
                fallback,
                matchedRule,
                attempts,
                fallbackGroups,
                callerKey,
                purpose,
                via);
    }

    public record Candidate(
            String provider,
            String account,
            String model,
            AiModel.Tier tier,
            boolean eligible,
            RouteEliminationReason reason,
            double healthScore,
            @Nullable String source) {

        public Candidate(
                String provider,
                String account,
                String model,
                AiModel.Tier tier,
                boolean eligible,
                RouteEliminationReason reason,
                double healthScore) {
            this(provider, account, model, tier, eligible, reason, healthScore, null);
        }
    }

    public record Classification(ComplexityLevel level, double confidence, AiModel.Tier tier) {
    }

    public record MatchedRule(
            @Nullable String name, @Nullable String condition, @Nullable String member, int priority) {
    }

    public record Attempt(
            @Nullable String group,
            String provider,
            String account,
            String model,
            AiModel.@Nullable Tier tier,
            @Nullable String source,
            int attempt,
            boolean success,
            @Nullable Integer status,
            @Nullable String errorCode,
            long latencyMillis,
            Instant at) {
    }
}