package cn.tolim.pudans.core.ai.routing.classifier;

import java.util.Objects;

public record ComplexityVerdict(ComplexityLevel level, double confidence) {

    public ComplexityVerdict {
        Objects.requireNonNull(level, "level");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence 必须位于 0 到 1 之间");
        }
    }
}
