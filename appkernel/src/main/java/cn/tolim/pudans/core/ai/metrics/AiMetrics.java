package cn.tolim.pudans.core.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiMetrics {

    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String STATUS_FAILED = "FAILED";

    public static final String STATUS_DEAD = "DEAD";

    private static final String METER_GATEWAY_REQUESTS = "ai.gateway.requests";

    private static final String METER_GATEWAY_LATENCY = "ai.gateway.latency";

    private static final String METER_GATEWAY_TOKENS = "ai.gateway.tokens";

    private static final String METER_GATEWAY_POINTS = "ai.gateway.points";

    private static final String METER_ROUTING_DECISIONS = "ai.routing.decisions";

    private static final String METER_MEMORY_TASKS = "ai.memory.tasks";

    private static final String METER_MEMORY_TASK_DURATION = "ai.memory.task.duration";

    private static final String METER_ALERTS = "ai.alerts";

    private static final String UNKNOWN = "unknown";

    private static final String SEVERITY_WARNING = "WARN";

    private static final String SEVERITY_ERROR = "ERROR";

    private static final String ALERT_PROVIDER_EXHAUSTED = "provider_exhausted";

    private static final String ALERT_ROUTE_EXHAUSTED = "route_exhausted";

    private static final String ALERT_TASK_DEAD = "task_dead";

    private static final String ALERT_WALLET_INSUFFICIENT = "wallet_insufficient";

    private static final String ALERT_MESSAGE = "AI 告警触发：type={}，severity={}。";

    private final MeterRegistry registry;

    public void gatewayRequest(
            @Nullable String protocol, @Nullable String agentSource, @Nullable String status) {
        safe(() -> Counter.builder(METER_GATEWAY_REQUESTS)
                .tags("protocol", tag(protocol), "agent_source", tag(agentSource), "status", tag(status))
                .register(registry)
                .increment());
    }

    public void gatewayLatency(
            @Nullable String protocol, @Nullable String status, @Nullable Duration duration) {
        safe(() -> Timer.builder(METER_GATEWAY_LATENCY)
                .tags("protocol", tag(protocol), "status", tag(status))
                .register(registry)
                .record(duration == null ? Duration.ZERO : duration));
    }

    public void gatewayTokens(
            @Nullable String provider,
            @Nullable String model,
            @Nullable Integer input,
            @Nullable Integer output,
            @Nullable Integer cache) {
        incrementTokens(provider, model, "input", input);
        incrementTokens(provider, model, "output", output);
        incrementTokens(provider, model, "cache", cache);
    }

    public void gatewayPoints(
            @Nullable BigDecimal points, @Nullable String provider, @Nullable String model) {
        if (points == null || points.signum() <= 0) {
            return;
        }
        safe(() -> Counter.builder(METER_GATEWAY_POINTS)
                .tags("provider", tag(provider), "model", tag(model))
                .register(registry)
                .increment(points.doubleValue()));
    }

    public void routingDecision(
            @Nullable String routeGroup, @Nullable String routeMode, @Nullable String outcome) {
        safe(() -> Counter.builder(METER_ROUTING_DECISIONS)
                .tags("route_group", tag(routeGroup), "route_mode", tag(routeMode), "outcome", tag(outcome))
                .register(registry)
                .increment());
    }

    public void memoryTask(@Nullable String taskType, @Nullable String status, @Nullable Duration duration) {
        var typeTag = tag(taskType);
        var statusTag = tag(status);
        safe(() -> Counter.builder(METER_MEMORY_TASKS)
                .tags("task_type", typeTag, "status", statusTag)
                .register(registry)
                .increment());
        if (duration == null) {
            return;
        }
        safe(() -> Timer.builder(METER_MEMORY_TASK_DURATION)
                .tags("task_type", typeTag, "status", statusTag)
                .register(registry)
                .record(duration));
    }

    public void alert(@Nullable String type, @Nullable String severity) {
        recordAlert(type, severity);
        log.warn(ALERT_MESSAGE, tag(type), tag(severity));
    }

    public void providerExhausted(@Nullable String routeGroup) {
        alertWithContext(
                ALERT_PROVIDER_EXHAUSTED, SEVERITY_WARNING, "routeGroup=" + tag(routeGroup));
    }

    public void routeExhausted(@Nullable String routeGroup) {
        alertWithContext(
                ALERT_ROUTE_EXHAUSTED, SEVERITY_WARNING, "routeGroup=" + tag(routeGroup));
    }

    public void taskDead(@Nullable String taskType, int retries) {
        alertWithContext(
                ALERT_TASK_DEAD, SEVERITY_ERROR, "taskType=" + tag(taskType) + "，retries=" + retries);
    }

    public void walletInsufficient() {
        alert(ALERT_WALLET_INSUFFICIENT, SEVERITY_WARNING);
    }

    private void alertWithContext(String type, String severity, String context) {
        recordAlert(type, severity);
        log.warn("AI 告警触发：type={}，severity={}，{}。", tag(type), tag(severity), context);
    }

    private void recordAlert(String type, String severity) {
        safe(() -> Counter.builder(METER_ALERTS)
                .tags("type", tag(type), "severity", tag(severity))
                .register(registry)
                .increment());
    }

    private void incrementTokens(
            @Nullable String provider, @Nullable String model, String direction, @Nullable Integer amount) {
        if (amount == null || amount <= 0) {
            return;
        }
        safe(() -> Counter.builder(METER_GATEWAY_TOKENS)
                .tags("direction", direction, "provider", tag(provider), "model", tag(model))
                .register(registry)
                .increment(amount.doubleValue()));
    }

    private static String tag(@Nullable String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private void safe(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            // 指标失败不能影响业务请求。
            log.warn("AI 指标记录失败。", error);
        }
    }
}
