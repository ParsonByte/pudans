package cn.tolim.pudans.core.live.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveMetrics {

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    private final AtomicLong activeSources = new AtomicLong();

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("live.sources.active", activeSources, AtomicLong::doubleValue)
                .register(registry);
    }

    public void sourcesActive(long count) {
        activeSources.set(Math.max(0, count));
    }

    public void resolveSuccess(@Nullable String platform) {
        counter("live.resolve.success", "platform", tag(platform)).increment();
    }

    public void resolveFail(@Nullable String platform, @Nullable String code) {
        counter("live.resolve.fail", "platform", tag(platform), "code", tag(code)).increment();
    }

    public void resolveRateLimited(@Nullable String platform) {
        counter("live.resolve.rate_limited", "platform", tag(platform)).increment();
    }

    public void resolveCacheHit(@Nullable String platform) {
        counter("live.resolve.cache.hit", "platform", tag(platform)).increment();
    }

    public void resolveRedisDegraded(@Nullable String platform) {
        counter("live.resolve.redis.degraded", "platform", tag(platform)).increment();
    }

    public void monitorTick(@Nullable Duration duration) {
        safe(() -> Timer.builder("live.monitor.tick.duration")
                .register(registry)
                .record(duration == null ? Duration.ZERO : duration));
    }

    public void segmentWriteFail() {
        counter("live.segment.write.fail").increment();
    }

    public void merge(@Nullable Duration duration, boolean success) {
        safe(() -> Timer.builder("live.merge.duration")
                .tag("result", success ? "success" : "failed")
                .register(registry)
                .record(duration == null ? Duration.ZERO : duration));
    }

    public void mergeFail() {
        counter("live.merge.fail").increment();
    }

    public void partUploadFail() {
        counter("live.part.upload.fail").increment();
    }

    public void storagePolicyInvalid() {
        counter("live.storage.policy.invalid").increment();
    }

    public void cleanupFailed() {
        counter("live.cleanup.failed").increment();
    }

    public void recordingBytes(@Nullable String platform, long bytes) {
        if (bytes <= 0) {
            return;
        }
        counter("live.recording.bytes.total", "platform", tag(platform)).increment(bytes);
    }

    public void alert(@Nullable String type) {
        counter("live.alerts", "type", tag(type)).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            log.warn("直播指标记录失败：{}", error.getMessage());
        }
    }

    private static String tag(@Nullable String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
