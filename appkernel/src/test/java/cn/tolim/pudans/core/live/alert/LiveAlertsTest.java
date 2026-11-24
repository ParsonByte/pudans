// 校验告警去重、Redis 故障放行与断流类告警绕过去重

package cn.tolim.pudans.core.live.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

class LiveAlertsTest {

    private static final Map<String, Object> PAYLOAD = Map.of("errorCode", "LIVE_STREAM_FAILED");

    private final ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);

    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);

    private final LiveMetrics metrics = mock(LiveMetrics.class);

    private final LiveAlerts alerts = new LiveAlerts(redisTemplate, new LiveProperties(), domainEventPublisher, metrics);

    @Test
    void alertPublishesFirstOccurrence() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        alerts.alert("live.recording.failed", "rec-1", PAYLOAD).block();

        verify(domainEventPublisher).publish("live.recording.failed", PAYLOAD, null);
        verify(metrics).alert("live.recording.failed");
    }

    @Test
    void alertSkipsDuplicateWithinWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(false));

        alerts.alert("live.recording.failed", "rec-1", PAYLOAD).block();

        verifyNoInteractions(domainEventPublisher, metrics);
    }

    @Test
    void alertStillPublishesWhenRedisFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        alerts.alert("live.recording.failed", "rec-1", PAYLOAD).block();

        verify(domainEventPublisher).publish("live.recording.failed", PAYLOAD, null);
    }

    @Test
    void alertAlwaysBypassesDedupe() {
        alerts.alertAlways("live.stream.interrupted", "rec-1", PAYLOAD).block();
        alerts.alertAlways("live.stream.interrupted", "rec-1", PAYLOAD).block();

        verifyNoInteractions(redisTemplate);
        verify(domainEventPublisher, times(2)).publish("live.stream.interrupted", PAYLOAD, null);
        verify(metrics, times(2)).alert("live.stream.interrupted");
    }

    @Test
    void ignoresAlertWithoutTypeOrSubject() {
        assertThat(alerts.alert("live.recording.failed", " ", PAYLOAD).block()).isNull();
        assertThat(alerts.alertAlways("", "rec-1", PAYLOAD).block()).isNull();

        verifyNoInteractions(redisTemplate, domainEventPublisher, metrics);
    }
}
