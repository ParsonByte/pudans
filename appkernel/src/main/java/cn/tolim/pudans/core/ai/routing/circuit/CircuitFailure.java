package cn.tolim.pudans.core.ai.routing.circuit;

import java.time.Instant;
import java.util.Objects;

public record CircuitFailure(Instant restUntil) {

    public static CircuitFailure defaultFailure() {
        return new CircuitFailure(null);
    }

    public static CircuitFailure until(Instant restUntil) {
        return new CircuitFailure(Objects.requireNonNull(restUntil, "restUntil"));
    }
}
