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

    private final AtomicReference<BigDecimal> units = new AtomicReference<>(BigDecimal.ZERO);

    private final AtomicReference<String> billingSource = new AtomicReference<>();

    private final AtomicInteger calls = new AtomicInteger();

    public int nextCall() {
        return calls.incrementAndGet();
    }

    public void record(AiGatewayResult result) {
        meta.set(result.meta());
        accumulateUsage(result.usage());
        recordUnits(result.units(), result.billingSource());
    }

    public void recordMeta(AiGatewayMeta meta) {
        this.meta.set(meta);
    }

    public void recordUsage(AiTokenUsage usage, @Nullable BigDecimal units, @Nullable String source) {
        accumulateUsage(usage);
        recordUnits(units, source);
    }

    private void accumulateUsage(@Nullable AiTokenUsage value) {
        if (value == null) {
            return;
        }
        usage.updateAndGet(current -> current == null
                ? value
                : new AiTokenUsage(
                        sum(current.inputTokens(), value.inputTokens()),
                        sum(current.outputTokens(), value.outputTokens()),
                        sum(current.cacheReadTokens(), value.cacheReadTokens()),
                        sum(current.cacheWriteTokens(), value.cacheWriteTokens())));
    }

    private static Integer sum(@Nullable Integer first, @Nullable Integer second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first + second;
    }

    private void recordUnits(@Nullable BigDecimal units, @Nullable String source) {
        if (units != null) {
            this.units.updateAndGet(current -> current == null ? units : current.add(units));
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

    public BigDecimal units() {
        return units.get() == null ? BigDecimal.ZERO : units.get();
    }

    public @Nullable String billingSource() {
        return billingSource.get();
    }
}