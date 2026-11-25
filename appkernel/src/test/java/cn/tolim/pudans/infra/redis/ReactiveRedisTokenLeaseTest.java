package cn.tolim.pudans.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReactiveRedisTokenLeaseTest {

    private static final String KEY = "pudans:test:lease:1";

    private static final Duration INITIAL_TTL = Duration.ofSeconds(15);

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private ReactiveRedisTokenLease leases;

    @BeforeEach
    void setUp() {
        leases = new ReactiveRedisTokenLease(redisTemplate);
    }

    @Test
    void acquiresCallerKeyedLeaseAndExposesTokenAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(INITIAL_TTL))).thenReturn(Mono.just(true));

        var lease = leases.acquire(KEY, INITIAL_TTL).block();

        assertThat(lease).isNotNull();
        assertThat(lease.key()).isEqualTo(KEY);
        assertThat(lease.ttl()).isEqualTo(INITIAL_TTL);
        assertThat(UUID.fromString(lease.token())).isNotNull();
        var token = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), token.capture(), eq(INITIAL_TTL));
        assertThat(token.getValue()).isEqualTo(lease.token());
    }

    @Test
    void returnsEmptyWhenAnotherOwnerHoldsTheLease() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(INITIAL_TTL))).thenReturn(Mono.just(false));

        StepVerifier.create(leases.acquire(KEY, INITIAL_TTL)).verifyComplete();
    }

    @Test
    void generatesANewTokenForEachAcquireSubscription() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(INITIAL_TTL))).thenReturn(Mono.just(true));
        var acquire = leases.acquire(KEY, INITIAL_TTL);

        var first = acquire.block();
        var second = acquire.block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.token()).isNotEqualTo(second.token());
        verify(valueOperations, times(2)).setIfAbsent(eq(KEY), anyString(), eq(INITIAL_TTL));
    }

    @Test
    void renewsOnlyThroughTheTokenCheckedScript() {
        var lease = new ReactiveRedisTokenLease.TokenLease(KEY, "token-1", INITIAL_TTL);
        when(redisTemplate.execute(
                        ArgumentMatchers.<RedisScript<Long>>argThat(script -> script
                                .getScriptAsString()
                                .contains("redis.call('get'")
                                && script.getScriptAsString().contains("redis.call('pexpire'")),
                        eq(List.of(KEY)),
                        eq(List.of("token-1", "30000"))))
                .thenReturn(Flux.just(1L));

        StepVerifier.create(leases.renew(lease, Duration.ofSeconds(30)))
                .expectNext(ReactiveRedisTokenLease.LeaseResult.APPLIED)
                .verifyComplete();
    }

    @Test
    void reportsLostLeaseWhenTokenNoLongerMatches() {
        var lease = new ReactiveRedisTokenLease.TokenLease(KEY, "token-1", INITIAL_TTL);
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyList()))
                .thenReturn(Flux.just(0L));

        StepVerifier.create(leases.renew(lease, Duration.ofSeconds(30)))
                .expectNext(ReactiveRedisTokenLease.LeaseResult.LOST)
                .verifyComplete();
    }

    @Test
    void releasesOnlyThroughTheTokenCheckedScript() {
        var lease = new ReactiveRedisTokenLease.TokenLease(KEY, "token-1", INITIAL_TTL);
        when(redisTemplate.execute(
                        ArgumentMatchers.<RedisScript<Long>>argThat(script -> script
                                .getScriptAsString()
                                .contains("redis.call('get'")
                                && script.getScriptAsString().contains("redis.call('del'")),
                        eq(List.of(KEY)),
                        eq(List.of("token-1"))))
                .thenReturn(Flux.just(1L));

        StepVerifier.create(leases.release(lease))
                .expectNext(ReactiveRedisTokenLease.LeaseResult.APPLIED)
                .verifyComplete();
    }

    @Test
    void reportsLostLeaseWhenReleaseFindsAnotherToken() {
        var lease = new ReactiveRedisTokenLease.TokenLease(KEY, "token-1", INITIAL_TTL);
        when(redisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyList()))
                .thenReturn(Flux.empty());

        StepVerifier.create(leases.release(lease))
                .expectNext(ReactiveRedisTokenLease.LeaseResult.LOST)
                .verifyComplete();
    }
}
