package cn.tolim.pudans.core.ai.routing;

import java.util.List;
import java.util.Objects;

public record RoutePlan(
        RouteSelection selected,
        List<RouteSelection> fallbackSeats,
        List<String> fallbackReferences,
        RouteTrace trace) {

    public RoutePlan {
        fallbackSeats = fallbackSeats == null ? List.of() : List.copyOf(fallbackSeats);
        fallbackReferences = fallbackReferences == null ? List.of() : List.copyOf(fallbackReferences);
        Objects.requireNonNull(trace, "trace");
    }

    public boolean routed() {
        return selected != null;
    }
}
