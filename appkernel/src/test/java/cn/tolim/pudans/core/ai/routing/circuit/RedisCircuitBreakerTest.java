package cn.tolim.pudans.core.ai.routing.circuit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RedisCircuitBreakerTest {

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveSetOperations<String, String> setOperations;

    @Mock
    ReactiveHashOperations<String, Object, Object> hashOperations;

    private RedisCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        breaker = new RedisCircuitBreaker(config(), redisTemplate);
    }

    @Test
    void publishesLocallyOpenedCircuits() {
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(hashOperations.putAll(anyString(), anyMap())).thenReturn(Mono.empty());
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.members(anyString())).thenReturn(Flux.empty());
        for (var index = 0; index < 4; index++) {
            breaker.recordFailure("provider-a", CircuitFailure.defaultFailure());
        }
        breaker.recordFailure("provider-a", CircuitFailure.defaultFailure());

        StepVerifier.create(breaker.syncOnce()).expectNext(0L).verifyComplete();

        assertThat(breaker.isOpen("provider-a")).isTrue();
        verify(hashOperations).putAll(eq("ai:circuit:provider-a"), anyMap());
    }

    @Test
    void adoptsOpenStateFromAnotherReplica() {
        var retryAt = Instant.now().plusSeconds(60);
        when(setOperations.members("ai:circuit:keys")).thenReturn(Flux.just("provider-b"));
        when(hashOperations.entries("ai:circuit:provider-b")).thenReturn(Flux.just(
                Map.entry((Object) "state", (Object) "OPEN"),
                Map.entry((Object) "retryAt", (Object) retryAt.toString())));

        StepVerifier.create(breaker.syncOnce()).expectNext(1L).verifyComplete();

        assertThat(breaker.isOpen("provider-b")).isTrue();
    }

    private static CircuitBreakerConfig config() {
        return new CircuitBreakerConfig(
                5, 2, Duration.ofSeconds(30), Duration.ofMinutes(5), 60000);
    }
}