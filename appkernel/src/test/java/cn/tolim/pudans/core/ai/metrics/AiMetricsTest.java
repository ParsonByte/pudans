package cn.tolim.pudans.core.ai.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AiMetricsTest {

    private SimpleMeterRegistry registry;

    private AiMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiMetrics(registry);
    }

    @Test
    void recordsGatewayRequestTagsAndCount() {
        metrics.gatewayRequest("OPENAI_CHAT", "pudans-chat", "SUCCESS");

        var counter = registry.find("ai.gateway.requests")
                .tag("protocol", "OPENAI_CHAT")
                .tag("agent_source", "pudans-chat")
                .tag("status", "SUCCESS")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordsGatewayLatency() {
        metrics.gatewayLatency("ANTHROPIC", "FAILED", Duration.ofMillis(40));

        var timer = registry.find("ai.gateway.latency")
                .tag("protocol", "ANTHROPIC")
                .tag("status", "FAILED")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(40.0);
    }

    @Test
    void recordsGatewayTokensPerDirection() {
        metrics.gatewayTokens("provider-a", "model-a", 10, 20, 5);

        assertThat(tokenCount("input")).isEqualTo(10.0);
        assertThat(tokenCount("output")).isEqualTo(20.0);
        assertThat(tokenCount("cache")).isEqualTo(5.0);
    }

    @Test
    void recordsGatewayPointsAsDecimalAmount() {
        metrics.gatewayPoints(new BigDecimal("2.5"), "provider-a", "model-a");

        var counter = registry.find("ai.gateway.points")
                .tag("provider", "provider-a")
                .tag("model", "model-a")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.5);
    }

    @Test
    void recordsRoutingDecisions() {
        metrics.routingDecision("group-a", "SMART", "SELECTED");

        var counter = registry.find("ai.routing.decisions")
                .tag("route_group", "group-a")
                .tag("route_mode", "SMART")
                .tag("outcome", "SELECTED")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordsMemoryTaskStatusAndDuration() {
        metrics.memoryTask("L1_EXTRACT", "SUCCESS", Duration.ofMillis(30));

        var counter = registry.find("ai.memory.tasks")
                .tag("task_type", "L1_EXTRACT")
                .tag("status", "SUCCESS")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        var timer = registry.find("ai.memory.task.duration")
                .tag("task_type", "L1_EXTRACT")
                .tag("status", "SUCCESS")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(30.0);
    }

    @Test
    void recordsAlertCounter() {
        metrics.alert("provider_exhausted", "WARN");

        assertThat(alertCount("provider_exhausted", "WARN")).isEqualTo(1.0);
    }

    @Test
    void fallsBackToUnknownForNullTags() {
        metrics.gatewayRequest(null, null, null);

        var counter = registry.find("ai.gateway.requests")
                .tag("protocol", "unknown")
                .tag("agent_source", "unknown")
                .tag("status", "unknown")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void alertMethodsRecordCountersAndLogSafeContext() {
        var logger = (Logger) LoggerFactory.getLogger(AiMetrics.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            metrics.providerExhausted("group-a");
            metrics.routeExhausted("group-a");
            metrics.taskDead("L1_EXTRACT", 3);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(alertCount("provider_exhausted", "WARN")).isEqualTo(1.0);
        assertThat(alertCount("route_exhausted", "WARN")).isEqualTo(1.0);
        assertThat(alertCount("task_dead", "ERROR")).isEqualTo(1.0);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("AI 告警触发")
                        .contains("provider_exhausted")
                        .contains("group-a"))
                .anySatisfy(message -> assertThat(message)
                        .contains("task_dead")
                        .contains("L1_EXTRACT"));
    }

    @Test
    void walletInsufficientRecordsWarningAlert() {
        metrics.walletInsufficient();

        assertThat(alertCount("wallet_insufficient", "WARN")).isEqualTo(1.0);
    }

    @Test
    void metricFailuresDoNotPropagate() {
        var failing = new SimpleMeterRegistry();
        failing.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                throw new IllegalStateException("指标注册失败");
            }
        });
        var failingMetrics = new AiMetrics(failing);

        assertThatCode(() -> {
            failingMetrics.gatewayRequest("OPENAI_CHAT", "pudans-chat", "SUCCESS");
            failingMetrics.gatewayTokens("provider-a", "model-a", 1, 1, 1);
            failingMetrics.alert("provider_exhausted", "WARN");
        }).doesNotThrowAnyException();
    }

    private double tokenCount(String direction) {
        var counter = registry.find("ai.gateway.tokens")
                .tag("direction", direction)
                .tag("provider", "provider-a")
                .tag("model", "model-a")
                .counter();
        assertThat(counter).isNotNull();
        return counter.count();
    }

    private double alertCount(String type, String severity) {
        var counter = registry.find("ai.alerts")
                .tag("type", type)
                .tag("severity", severity)
                .counter();
        assertThat(counter).isNotNull();
        return counter.count();
    }
}
