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

    // 按挑战标识保存答案并设置过期时间
    public Mono<Void> save(String challengeId, String answer, Duration ttl) {
        return redisTemplate
                .opsForValue()
                .set(KEY_PREFIX + challengeId, answer, ttl)
                .then();
    }

    // 取值与删除在 Redis 端原子完成，保证验证码只能被消费一次
    public Mono<String> take(String challengeId) {
        return redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + challengeId);
    }
}
