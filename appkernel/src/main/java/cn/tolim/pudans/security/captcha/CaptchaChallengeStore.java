package cn.tolim.pudans.security.captcha;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CaptchaChallengeStore {

    private static final String KEY_PREFIX = "pudans:captcha:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> save(String challengeId, String answer, Duration ttl) {
        return redisTemplate
                .opsForValue()
                .set(KEY_PREFIX + challengeId, answer, ttl)
                .then();
    }

    public Mono<String> get(String challengeId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + challengeId);
    }

    public Mono<Boolean> delete(String challengeId) {
        return redisTemplate.delete(KEY_PREFIX + challengeId).map(count -> count > 0);
    }
}
