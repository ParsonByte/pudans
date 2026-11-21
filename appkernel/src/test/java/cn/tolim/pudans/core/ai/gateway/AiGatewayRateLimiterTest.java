package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.infra.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGatewayRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    private AiGatewayRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AiGatewayRateLimiter(null);
        limiter.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void enforcesTheRollingMinuteRpmPerProviderAndRecoversAfterTheWindow() {
        var provider = provider("provider-a", 2, 0);
        var account = account("account-a", 0, 0);

        StepVerifier.create(limiter.acquire(provider, account)).expectNextCount(1).verifyComplete();
        StepVerifier.create(limiter.acquire(provider, account)).expectNextCount(1).verifyComplete();
        StepVerifier.create(limiter.acquire(provider, account))
                .expectErrorSatisfies(AiGatewayRateLimiterTest::assertRateLimited)
                .verify();

        limiter.setClock(Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));

        StepVerifier.create(limiter.acquire(provider, account)).expectNextCount(1).verifyComplete();
    }

    @Test
    void enforcesUserConcurrencyLimits() {
        var permit = limiter.acquireUser("user-1", 1).block();

        StepVerifier.create(limiter.acquireUser("user-1", 1))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo(AiGatewayRateLimiter.CODE_USER_LIMITED);
                })
                .verify();

        limiter.release(permit).block();

        StepVerifier.create(limiter.acquireUser("user-1", 1))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void enforcesUserRpmAndRecoversAfterTheWindow() {
        StepVerifier.create(limiter.acquireUserRpm("user-1", 2)).verifyComplete();
        StepVerifier.create(limiter.acquireUserRpm("user-1", 2)).verifyComplete();
        StepVerifier.create(limiter.acquireUserRpm("user-1", 2))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(AiGatewayRateLimiter.CODE_USER_LIMITED))
                .verify();

        limiter.setClock(Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));

        StepVerifier.create(limiter.acquireUserRpm("user-1", 2)).verifyComplete();
    }

    @Test
    void combinesUserRpmAndConcurrencyLimits() {
        var permit = limiter.acquireUser("user-1", 1, 2).block();
        assertThat(permit).isNotNull();

        StepVerifier.create(limiter.acquireUser("user-1", 1, 2))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(AiGatewayRateLimiter.CODE_USER_LIMITED))
                .verify();

        limiter.release(permit).block();

        StepVerifier.create(limiter.acquireUser("user-1", 1, 2))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(AiGatewayRateLimiter.CODE_USER_LIMITED))
                .verify();
    }

    @Test
    void enforcesTheRollingMinuteRpmPerAccount() {
        var provider = provider("provider-a", 0, 0);
        var account = account("account-a", 1, 0);

        StepVerifier.create(limiter.acquire(provider, account)).expectNextCount(1).verifyComplete();
        StepVerifier.create(limiter.acquire(provider, account))
                .expectErrorSatisfies(error -> {
                    assertRateLimited(error);
                    assertThat(((ApiException) error).getReason()).contains("account");
                })
                .verify();
    }

    @Test
    void holdsAndReleasesConcurrencyPermits() {
        var provider = provider("provider-a", 0, 1);
        var account = account("account-a", 0, 0);

        var permit = limiter.acquire(provider, account).block();
        assertThat(permit).isNotNull();
        assertThat(permit.slots()).hasSize(1);
        StepVerifier.create(limiter.acquire(provider, account))
                .expectErrorSatisfies(error -> {
                    assertRateLimited(error);
                    assertThat(((ApiException) error).getReason()).contains("provider-a")
                            .contains("并发");
                })
                .verify();

        StepVerifier.create(limiter.release(permit)).verifyComplete();
        StepVerifier.create(limiter.acquire(provider, account)).expectNextCount(1).verifyComplete();
    }

    @Test
    void ignoresLimitsThatAreNotConfigured() {
        var provider = provider("provider-a", 0, 0);
        var account = account("account-a", 0, 0);

        StepVerifier.create(limiter.acquire(provider, account))
                .assertNext(permit -> assertThat(permit.slots()).isEmpty())
                .verifyComplete();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void fallsBackToProcessLocalCountersWhenRedisFails() {
        var redisLimiter = new AiGatewayRateLimiter(redisTemplate);
        redisLimiter.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        var provider = provider("provider-a", 0, 1);
        var account = account("account-a", 0, 0);
        doReturn(Flux.error(new IllegalStateException("redis down")))
                .when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), anyList());

        StepVerifier.create(redisLimiter.acquire(provider, account))
                .assertNext(permit -> assertThat(permit.slots())
                        .isNotEmpty()
                        .allMatch(AiGatewayRateLimiter.Slot::local))
                .verifyComplete();
        StepVerifier.create(redisLimiter.acquire(provider, account))
                .expectErrorSatisfies(AiGatewayRateLimiterTest::assertRateLimited)
                .verify();
    }

    @Test
    void usesRedisSlotsWhenRedisAcceptsThePermit() {
        var redisLimiter = new AiGatewayRateLimiter(redisTemplate);
        redisLimiter.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        var provider = provider("provider-a", 0, 1);
        var account = account("account-a", 0, 0);
        doReturn(Flux.just(1L))
                .when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), anyList());

        var permit = redisLimiter.acquire(provider, account).block();

        assertThat(permit).isNotNull();
        assertThat(permit.slots()).isNotEmpty();
        assertThat(permit.slots()).allMatch(slot -> !slot.local());
        StepVerifier.create(redisLimiter.release(permit)).verifyComplete();
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(any(RedisScript.class), anyList(), anyList());
    }

    @Test
    void releasesNothingForAnEmptyPermit() {
        StepVerifier.create(limiter.release(null)).verifyComplete();
        StepVerifier.create(limiter.release(AiGatewayRateLimiter.Permit.empty())).verifyComplete();
    }

    private static void assertRateLimited(Throwable error) {
        assertThat(error).isInstanceOf(ApiException.class);
        var api = (ApiException) error;
        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(api.getCode()).isEqualTo(AiGatewayRateLimiter.CODE_RATE_LIMITED);
    }

    private static AiProvider provider(String name, int maxRpm, int maxConcurrency) {
        var provider = new AiProvider();
        provider.setMetadata(metadata(name));
        provider.getSpec().setDisplayName(name);
        provider.getSpec().setMaxRPM(maxRpm);
        provider.getSpec().setMaxConcurrency(maxConcurrency);
        return provider;
    }

    private static AiProviderAccount account(String name, int maxRpm, int maxConcurrency) {
        var account = new AiProviderAccount();
        account.setMetadata(metadata(name));
        account.getSpec().setProvider("provider-a");
        account.getSpec().setDisplayName(name);
        account.getSpec().setMaxRPM(maxRpm);
        account.getSpec().setMaxConcurrency(maxConcurrency);
        return account;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}