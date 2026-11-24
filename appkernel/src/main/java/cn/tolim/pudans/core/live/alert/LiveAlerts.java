package cn.tolim.pudans.core.live.alert;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 直播告警统一出口：Redis 去重后复用领域事件，Redis 故障时允许重复发送
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveAlerts {

    private static final String DEDUPE_PREFIX = "pudans:live:alert:";

    private static final String RATE_LIMIT_WINDOW_KEY = "pudans:live:ratelimit:rooms";

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final LiveProperties properties;

    private final DomainEventPublisher domainEventPublisher;

    private final LiveMetrics metrics;

    // 按类型与主体去重后发送领域事件告警
    public Mono<Void> alert(String type, String subject, Map<String, Object> payload) {
        if (!validSubject(type, subject)) {
            return Mono.empty();
        }
        var dedupeKey = DEDUPE_PREFIX + type + ":" + subject;
        return Mono.defer(() -> redisTemplate
                        .opsForValue()
                        .setIfAbsent(dedupeKey, "1", properties.getAlert().getDedupe()))
                .onErrorResume(error -> {
                    log.warn("直播告警去重失败，继续发送：{}", error.getMessage());
                    return Mono.just(Boolean.TRUE);
                })
                .flatMap(first -> {
                    if (!Boolean.TRUE.equals(first)) {
                        return Mono.empty();
                    }
                    publish(type, payload);
                    return Mono.empty();
                });
    }

    // 断流与恢复按事件实时发送，不经过 Redis 去重
    public Mono<Void> alertAlways(String type, String subject, Map<String, Object> payload) {
        if (!validSubject(type, subject)) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> publish(type, payload));
    }

    // 类型与主体都非空才允许发送，空值时只记录日志
    private boolean validSubject(String type, String subject) {
        if (StringUtils.hasText(type) && StringUtils.hasText(subject)) {
            return true;
        }
        log.warn("直播告警缺少类型或主体，已忽略：type={}，subject={}", type, subject);
        return false;
    }

    // 同一房间连续限流按房间去重告警，窗口内多个房间被限流再发全局告警
    public Mono<Void> rateLimited(LivePlatform platform, String roomId, Instant cooldownUntil, long streak) {
        if (!StringUtils.hasText(roomId)) {
            return Mono.empty();
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("roomId", roomId);
        payload.put("cooldownUntil", cooldownUntil == null ? null : cooldownUntil.toString());
        payload.put("streak", streak);
        payload.put("at", Instant.now().toString());
        return alert("live.resolve.rate_limited", roomId, payload)
                .then(trackRateLimitedRoom(roomId))
                .then();
    }

    // 滑动窗口内统计被限流房间，超过阈值发全局告警
    private Mono<Void> trackRateLimitedRoom(String roomId) {
        var now = Instant.now();
        var cutoff = now.minus(RATE_LIMIT_WINDOW).toEpochMilli();
        return redisTemplate
                .opsForZSet()
                .add(RATE_LIMIT_WINDOW_KEY, roomId, now.toEpochMilli())
                .then(redisTemplate
                        .opsForZSet()
                        .removeRangeByScore(
                                RATE_LIMIT_WINDOW_KEY, Range.closed(0D, (double) cutoff)))
                .then(redisTemplate.expire(RATE_LIMIT_WINDOW_KEY, RATE_LIMIT_WINDOW.plusMinutes(1)))
                .then(redisTemplate.opsForZSet().size(RATE_LIMIT_WINDOW_KEY))
                .flatMap(count -> {
                    if (count == null || count < properties.getAlert().getRateLimitThreshold()) {
                        return Mono.empty();
                    }
                    var payload = new LinkedHashMap<String, Object>();
                    payload.put("roomId", roomId);
                    payload.put("distinctRooms", count);
                    payload.put("windowSeconds", RATE_LIMIT_WINDOW.toSeconds());
                    payload.put("at", now.toString());
                    return alert("live.resolve.rate_limited", "global", payload);
                })
                .onErrorResume(error -> {
                    log.warn("直播限流窗口统计失败：{}", error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // 补全载荷后发布领域事件并计数
    private void publish(String type, Map<String, Object> payload) {
        var body = payload == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(payload);
        domainEventPublisher.publish(type, body, null);
        metrics.alert(type);
    }
}
