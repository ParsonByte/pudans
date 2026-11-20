package cn.tolim.pudans.core.ai.harness.observability;

import java.util.Map;
import java.util.Objects;

public record HarnessMetric(String name, Kind kind, double value, Map<String, String> tags) {

    public enum Kind {
        COUNTER,
        TIMER
    }

    public HarnessMetric {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("指标名称不能为空。");
        }
        Objects.requireNonNull(kind, "指标类型不能为空");
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("指标值必须是非负有限数。");
        }
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}