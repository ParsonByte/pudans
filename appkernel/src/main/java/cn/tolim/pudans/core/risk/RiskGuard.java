package cn.tolim.pudans.core.risk;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RiskGuard {

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

    // 生成场景与主体对应的限流窗口键。
    public static String windowKey(String scene, String subject) {
        return WINDOW_KEY_PREFIX + scene + ':' + subject;
    }

    // 生成场景与主体对应的封禁键。
    public static String banKey(String scene, String subject) {
        return BAN_KEY_PREFIX + scene + ':' + subject;
    }

    // 生成告警去重标记的 Redis 键。
    public static String alertKey(String scene, String subject) {
        return ALERT_KEY_PREFIX + scene + ':' + subject;
    }

    // 执行滑动窗口限流，并返回是否放行。
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
                .map(value -> RiskDecision.parse(value, rule.blocking() ? rule.getBlockSeconds() : 0))
                .defaultIfEmpty(RiskDecision.permit());
    }

    // 判断主体在指定场景下是否已被封禁。
    public Mono<Boolean> isBanned(String scene, String subject) {
        return redisTemplate.hasKey(banKey(scene, subject));
    }
    // 解封时同时清除失败计数，避免解封后立刻再次达到阈值。
    public Mono<Long> unban(String scene, String subject) {
        return redisTemplate.delete(banKey(scene, subject), windowKey(scene, subject));
    }

    // 按场景顺序返回首个命中的封禁场景。
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

    // 扫描并列出仍在有效期内的封禁记录。
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

        // 从 Redis 键还原封禁场景、主体与剩余时长。
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

    // 为主体写入带过期时间的封禁标记。
    public Mono<Void> ban(String scene, String subject, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return Mono.empty();
        }
        return redisTemplate
                .opsForValue()
                .set(banKey(scene, subject), "1", duration)
                .then();
    }

    // 以默认五分钟去重窗口标记告警已发送。
    public Mono<Boolean> markAlerted(String scene, String subject, Duration ttl) {
        var effectiveTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(5) : ttl;
        return redisTemplate
                .opsForValue()
                .setIfAbsent(alertKey(scene, subject), "1", effectiveTtl)
                .defaultIfEmpty(false);
    }
}
