package cn.tolim.pudans.core.risk.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlService {

    private static final String WINDOW_KEY_PREFIX = "pudans:risk:window:";

    private static final String BAN_KEY_PREFIX = "pudans:risk:ban:";

    private static final String ALERT_KEY_PREFIX = "pudans:risk:alert:";

    private static final RedisScript<String> WINDOW_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local retry = window
                if oldest[2] then
                    retry = tonumber(oldest[2]) + window - now
                end
                if retry < 0 then
                    retry = 0
                end
                return '0|' .. string.format('%d', count) .. '|' .. string.format('%d', retry)
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return '1|' .. string.format('%d', count + 1) .. '|' .. string.format('%d', window)
            """, String.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final RiskRuleRegistry ruleRegistry;

    public static String windowKey(String scene, String subject) {
        return WINDOW_KEY_PREFIX + scene + ':' + subject;
    }

    public static String banKey(String scene, String subject) {
        return BAN_KEY_PREFIX + scene + ':' + subject;
    }

    public static String alertKey(String scene, String subject) {
        return ALERT_KEY_PREFIX + scene + ':' + subject;
    }

    public Mono<RiskDecision> evaluate(String scene, String subject) {
        var rule = ruleRegistry.rule(scene);
        if (rule == null || !rule.isEnabled()) {
            return Mono.just(RiskDecision.permit());
        }
        var windowMillis = Math.max(1L, rule.getWindowSeconds()) * 1000L;
        var threshold = Math.max(1L, rule.getThreshold());
        var args = List.of(
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowMillis),
                String.valueOf(threshold),
                UUID.randomUUID().toString());
        return redisTemplate
                .execute(WINDOW_SCRIPT, List.of(windowKey(scene, subject)), args)
                .next()
                .map(value -> RiskDecision.parse(value, rule.getBlockSeconds()))
                .defaultIfEmpty(RiskDecision.permit());
    }

    public Mono<Boolean> isBanned(String scene, String subject) {
        return redisTemplate.hasKey(banKey(scene, subject));
    }

    public Mono<Long> unban(String scene, String subject) {
        return redisTemplate.delete(banKey(scene, subject));
    }

    public Mono<String> firstBannedScene(java.util.Collection<String> scenes, String subject) {
        if (scenes == null || scenes.isEmpty()) {
            return Mono.empty();
        }
        var sceneList = new java.util.ArrayList<>(scenes);
        var keys = sceneList.stream().map(scene -> banKey(scene, subject)).toList();
        return redisTemplate
                .opsForValue()
                .multiGet(keys)
                .flatMap(values -> {
                    for (var i = 0; i < values.size(); i++) {
                        if (values.get(i) != null) {
                            return Mono.just(sceneList.get(i));
                        }
                    }
                    return Mono.empty();
                });
    }

    public Flux<RiskBan> listBans() {
        return redisTemplate
                .scan(ScanOptions.scanOptions()
                        .match(BAN_KEY_PREFIX + "*")
                        .count(200)
                        .build())
                .flatMap(key -> redisTemplate
                        .getExpire(key)
                        .flatMap(ttl -> {
                            var ban = RiskBan.fromKey(key, ttl);
                            return ban == null ? Mono.empty() : Mono.just(ban);
                        }))
                .filter(ban -> ban.expiresInSeconds() > 0);
    }

    public record RiskBan(String scene, String subject, long expiresInSeconds) {

        static RiskBan fromKey(String key, Duration ttl) {
            if (ttl == null || ttl.isZero() || ttl.isNegative() || !key.startsWith(BAN_KEY_PREFIX)) {
                return null;
            }
            var rest = key.substring(BAN_KEY_PREFIX.length());
            var separator = rest.indexOf(':');
            if (separator <= 0 || separator == rest.length() - 1) {
                return null;
            }
            return new RiskBan(
                    rest.substring(0, separator), rest.substring(separator + 1), ttl.toSeconds());
        }
    }

    public Mono<Void> ban(String scene, String subject, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return Mono.empty();
        }
        return redisTemplate
                .opsForValue()
                .set(banKey(scene, subject), "1", duration)
                .then();
    }

    public Mono<Boolean> markAlerted(String scene, String subject, Duration ttl) {
        var effectiveTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(5) : ttl;
        return redisTemplate
                .opsForValue()
                .setIfAbsent(alertKey(scene, subject), "1", effectiveTtl)
                .defaultIfEmpty(false);
    }
}
