package cn.tolim.pudans.infra.cache;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class ReactiveRedisCacheStore {

    private final ReactiveStringRedisTemplate redisTemplate;

    // 读取缓存值，未命中时返回空流。
    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 写入带过期时间的缓存值，避免数据长期滞留。
    public Mono<Void> put(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue().set(key, value, ttl).then();
    }

    // 删除指定缓存键，立即失效。
    public Mono<Void> evict(String key) {
        return redisTemplate.delete(key).then();
    }
}
