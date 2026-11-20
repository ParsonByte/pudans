package cn.tolim.pudans.core.ai.harness.observability;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record HarnessSpan(
        String spanId,
        @Nullable String parentSpanId,
        HarnessSpanKind kind,
        String name,
        String runId,
        Map<String, String> attributes,
        Instant startedAt,
        @Nullable Instant endedAt,
        HarnessSpanStatus status,
        @Nullable String errorType) {

    public HarnessSpan {
        if (spanId == null || spanId.isBlank()) {
            throw new IllegalArgumentException("Span 标识不能为空。");
        }
        Objects.requireNonNull(kind, "Span 类型不能为空");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Span 名称不能为空。");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("运行标识不能为空。");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        Objects.requireNonNull(startedAt, "Span 开始时间不能为空");
        Objects.requireNonNull(status, "Span 状态不能为空");
        if (status == HarnessSpanStatus.STARTED && endedAt != null) {
            throw new IllegalArgumentException("进行中的 Span 不能有结束时间。");
        }
        if (status != HarnessSpanStatus.STARTED && endedAt == null) {
            throw new IllegalArgumentException("已结束的 Span 必须有结束时间。");
        }
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Span 结束时间不能早于开始时间。");
        }
    }

    public static HarnessSpan started(
            String spanId,
            @Nullable String parentSpanId,
            HarnessSpanKind kind,
            String name,
            String runId,
            Map<String, String> attributes,
            Instant startedAt) {
        return new HarnessSpan(
                spanId,
                parentSpanId,
                kind,
                name,
                runId,
                attributes,
                startedAt,
                null,
                HarnessSpanStatus.STARTED,
                null);
    }

    public HarnessSpan ended(
            HarnessSpanStatus status, @Nullable String errorType, Instant endedAt) {
        return new HarnessSpan(
                spanId,
                parentSpanId,
                kind,
                name,
                runId,
                attributes,
                startedAt,
                endedAt,
                status,
                errorType);
    }
}