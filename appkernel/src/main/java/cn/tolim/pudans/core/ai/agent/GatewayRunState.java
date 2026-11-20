package cn.tolim.pudans.core.ai.agent;

import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class GatewayRunState {

    private final AtomicReference<AiGatewayMeta> meta = new AtomicReference<>();

    private final AtomicReference<AiTokenUsage> usage = new AtomicReference<>();

    private final AtomicReference<BigDecimal> points = new AtomicReference<>(BigDecimal.ZERO);

    private final AtomicReference<String> billingSource = new AtomicReference<>();

    private final AtomicInteger calls = new AtomicInteger();

    public int nextCall() {
        return calls.incrementAndGet();
    }

    public void record(AiGatewayResult result) {
        meta.set(result.meta());
        usage.set(result.usage());
        recordPoints(result.points(), result.billingSource());
    }

    public void recordMeta(AiGatewayMeta meta) {
        this.meta.set(meta);
    }

    public void recordUsage(AiTokenUsage usage, @Nullable BigDecimal points, @Nullable String source) {
        if (usage != null) {
            this.usage.set(usage);
        }
        recordPoints(points, source);
    }

    private void recordPoints(@Nullable BigDecimal points, @Nullable String source) {
        if (points != null) {
            this.points.updateAndGet(current -> current == null ? points : current.add(points));
        }
        if (source != null) {
            billingSource.compareAndSet(null, source);
        }
    }

    public @Nullable AiGatewayMeta meta() {
        return meta.get();
    }

    public @Nullable AiTokenUsage usage() {
        return usage.get();
    }

    public BigDecimal points() {
        return points.get() == null ? BigDecimal.ZERO : points.get();
    }

    public @Nullable String billingSource() {
        return billingSource.get();
    }
}