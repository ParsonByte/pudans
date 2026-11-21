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

    // 注册活动直播源数量仪表，供看板采集
    @PostConstruct
    public void registerGauges() {
        Gauge.builder("live.sources.active", activeSources, AtomicLong::doubleValue)
                .register(registry);
    }

    // 更新活动直播源数量，负数按零处理
    public void sourcesActive(long count) {
        activeSources.set(Math.max(0, count));
    }

    // 统计一次成功的直播源解析
    public void resolveSuccess(@Nullable String platform) {
        counter("live.resolve.success", "platform", tag(platform)).increment();
    }

    // 按平台与错误码统计解析失败
    public void resolveFail(@Nullable String platform, @Nullable String code) {
        counter("live.resolve.fail", "platform", tag(platform), "code", tag(code)).increment();
    }

    // 统计被平台限流的解析次数
    public void resolveRateLimited(@Nullable String platform) {
        counter("live.resolve.rate_limited", "platform", tag(platform)).increment();
    }

    // 统计命中解析缓存的次数
    public void resolveCacheHit(@Nullable String platform) {
        counter("live.resolve.cache.hit", "platform", tag(platform)).increment();
    }

    // 统计 Redis 降级后继续解析的次数
    public void resolveRedisDegraded(@Nullable String platform) {
        counter("live.resolve.redis.degraded", "platform", tag(platform)).increment();
    }

    // 记录一次监控 tick 的耗时，指标失败不影响主流程
    public void monitorTick(@Nullable Duration duration) {
        safe(() -> Timer.builder("live.monitor.tick.duration")
                .register(registry)
                .record(duration == null ? Duration.ZERO : duration));
    }

    // 统计分片写入失败次数
    public void segmentWriteFail() {
        counter("live.segment.write.fail").increment();
    }

    // 按结果记录合并耗时，指标异常不影响合并
    public void merge(@Nullable Duration duration, boolean success) {
        safe(() -> Timer.builder("live.merge.duration")
                .tag("result", success ? "success" : "failed")
                .register(registry)
                .record(duration == null ? Duration.ZERO : duration));
    }

    // 统计合并失败次数
    public void mergeFail() {
        counter("live.merge.fail").increment();
    }

    // 统计成品分片上传失败次数
    public void partUploadFail() {
        counter("live.part.upload.fail").increment();
    }

    // 统计存储策略不可用的次数
    public void storagePolicyInvalid() {
        counter("live.storage.policy.invalid").increment();
    }

    // 统计录制清理失败次数
    public void cleanupFailed() {
        counter("live.cleanup.failed").increment();
    }

    // 累计写入对象存储的字节数
    public void recordingBytes(@Nullable String platform, long bytes) {
        if (bytes <= 0) {
            return;
        }
        counter("live.recording.bytes.total", "platform", tag(platform)).increment(bytes);
    }

    // 按告警类型统计发送次数
    public void alert(@Nullable String type) {
        counter("live.alerts", "type", tag(type)).increment();
    }

    // 按名称与标签取或创建计数器
    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    // 指标记录失败只告警，避免影响录制主流程
    private void safe(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            log.warn("直播指标记录失败：{}", error.getMessage());
        }
    }

    // 空标签统一替换为 unknown，保证指标维度稳定
    private static String tag(@Nullable String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
