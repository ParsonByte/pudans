package cn.tolim.pudans.core.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RiskGuardTest {

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    @Mock
    RiskRuleRegistry ruleRegistry;

    private RiskGuard service;

    @BeforeEach
    void setUp() {
        service = new RiskGuard(redisTemplate, ruleRegistry);
    }

    @Test
    void isBannedReadsTheSceneSpecificBanKey() {
        when(redisTemplate.hasKey("pudans:risk:ban:request-flood:1.2.3.4")).thenReturn(Mono.just(true));

        StepVerifier.create(service.isBanned(RiskScene.REQUEST_FLOOD, "1.2.3.4"))
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).hasKey("pudans:risk:ban:request-flood:1.2.3.4");
    }

    @Test
    void firstBannedSceneChecksEveryConfiguredSceneInOneRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Mono.just(Arrays.asList(null, null, "1")));

        StepVerifier.create(service.firstBannedScene(
                        List.of(RiskScene.REQUEST_FLOOD, RiskScene.AUTH_FAILURE, RiskScene.SERVER_ERROR),
                        "1.2.3.4"))
                .expectNext(RiskScene.SERVER_ERROR)
                .verifyComplete();

        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(List.class);
        verify(valueOperations).multiGet(captor.capture());
        assertThat(captor.getValue())
                .containsExactly(
                        "pudans:risk:ban:request-flood:1.2.3.4",
                        "pudans:risk:ban:auth-failure:1.2.3.4",
                        "pudans:risk:ban:server-error:1.2.3.4");
    }

    @Test
    void firstBannedSceneIsEmptyWhenNoSceneIsBanned() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Mono.just(Arrays.asList(null, null)));

        StepVerifier.create(service.firstBannedScene(
                        List.of(RiskScene.REQUEST_FLOOD, RiskScene.AUTH_FAILURE), "1.2.3.4"))
                .verifyComplete();
    }

    @Test
    void firstBannedSceneWithNoScenesNeverTouchesRedis() {
        StepVerifier.create(service.firstBannedScene(List.of(), "1.2.3.4")).verifyComplete();
    }

    @Test
    void unbanDeletesTheBanKeyAndClearsTheRiskWindow() {
        var banKey = "pudans:risk:ban:request-flood:1.2.3.4";
        var windowKey = "pudans:risk:window:request-flood:1.2.3.4";
        when(redisTemplate.delete(banKey, windowKey)).thenReturn(Mono.just(2L));

        StepVerifier.create(service.unban(RiskScene.REQUEST_FLOOD, "1.2.3.4"))
                .expectNext(2L)
                .verifyComplete();

        verify(redisTemplate).delete(banKey, windowKey);
    }

    @Test
    void unbanReturnsZeroWhenNeitherBanNorWindowExists() {
        var banKey = "pudans:risk:ban:login-failure:5.6.7.8";
        var windowKey = "pudans:risk:window:login-failure:5.6.7.8";
        when(redisTemplate.delete(banKey, windowKey)).thenReturn(Mono.just(0L));

        StepVerifier.create(service.unban(RiskScene.LOGIN_FAILURE, "5.6.7.8"))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void listBansParsesActiveBanKeysAndSkipsExpiredOnes() {
        var activeKey = "pudans:risk:ban:request-flood:1.2.3.4";
        var expiredKey = "pudans:risk:ban:login-failure:5.6.7.8";
        when(redisTemplate.scan(org.mockito.ArgumentMatchers.any(ScanOptions.class)))
                .thenReturn(Flux.just(activeKey, expiredKey));
        when(redisTemplate.getExpire(activeKey)).thenReturn(Mono.just(Duration.ofSeconds(30)));
        when(redisTemplate.getExpire(expiredKey)).thenReturn(Mono.just(Duration.ZERO));

        StepVerifier.create(service.listBans())
                .expectNext(new RiskGuard.RiskBan(RiskScene.REQUEST_FLOOD, "1.2.3.4", 30))
                .verifyComplete();
    }

    @Test
    void listBansKeepsIpv6SubjectsIntact() {
        var key = "pudans:risk:ban:request-flood:2001:db8::1";
        when(redisTemplate.scan(org.mockito.ArgumentMatchers.any(ScanOptions.class)))
                .thenReturn(Flux.just(key));
        when(redisTemplate.getExpire(key)).thenReturn(Mono.just(Duration.ofSeconds(45)));

        StepVerifier.create(service.listBans())
                .expectNext(new RiskGuard.RiskBan(
                        RiskScene.REQUEST_FLOOD, "2001:db8::1", 45))
                .verifyComplete();
    }
}
