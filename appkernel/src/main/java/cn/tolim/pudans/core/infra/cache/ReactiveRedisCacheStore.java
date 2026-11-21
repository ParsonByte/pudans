package cn.tolim.pudans.core.infra.cache;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class ReactiveRedisCacheStore {

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Mono<Void> put(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl).then();
    }

    public Mono<Void> evict(String key) {
        return redisTemplate.delete(key).then();
    }
}
