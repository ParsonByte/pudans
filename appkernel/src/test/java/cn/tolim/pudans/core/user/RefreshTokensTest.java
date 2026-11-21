package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.time.Duration;
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
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RefreshTokensTest {

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    @Mock
    ParameterGroups parameterGroups;

    @Mock
    PersonalTokens personalTokens;

    @Mock
    UserRoles userRoles;

    @Mock
    ReactiveUserDetailsService reactiveUserDetailsService;

    private RefreshTokens service;

    @BeforeEach
    void setUp() {
        // lenient() 让公共打桩不参与严格检查，避免未使用时报错。
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RefreshTokens(
                redisTemplate, parameterGroups, personalTokens, userRoles, reactiveUserDetailsService);
    }

    // 构造启用状态的用户详情
    private static UserDetails enabledUser() {
        return User.withUsername("alice").password("x").authorities(List.of()).build();
    }

    @Test
    void issuesARefreshTokenWithTheConfiguredTtl() {
        when(parameterGroups.getServer()).thenReturn(Mono.just(server(900, 3600)));
        when(valueOperations.set(anyString(), eq("alice"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.issue("alice"))
                .assertNext(token -> assertThat(token).isNotBlank())
                .verifyComplete();

        var ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(anyString(), eq("alice"), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void rotatesTheRefreshTokenAndIssuesAnAccessToken() {
        when(parameterGroups.getServer()).thenReturn(Mono.just(server(900, 3600)));
        when(valueOperations.getAndDelete(anyString())).thenReturn(Mono.just("alice"));
        when(reactiveUserDetailsService.findByUsername("alice")).thenReturn(Mono.just(enabledUser()));
        var pat = new PersonalAccessToken();
        when(userRoles.list("alice")).thenReturn(Flux.just("normal-role"));
        when(personalTokens.create(any(PersonalAccessToken.class), eq("alice"))).thenReturn(Mono.just(pat));
        when(personalTokens.generateToken(pat)).thenReturn(Mono.just("access-token"));
        when(personalTokens.deleteExpired("alice")).thenReturn(Mono.just(0L));
        when(valueOperations.set(anyString(), eq("alice"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.rotate("old-refresh-token"))
                .assertNext(result -> {
                    assertThat(result.accessToken()).isEqualTo("access-token");
                    assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("old-refresh-token");
                    assertThat(result.accessTokenTtlSeconds()).isEqualTo(900);
                    assertThat(result.refreshTokenTtlSeconds()).isEqualTo(3600);
                })
                .verifyComplete();

        verify(personalTokens).deleteExpired("alice");
    }

    @Test
    void expiredTokensAreRejected() {
        when(parameterGroups.getServer()).thenReturn(Mono.just(server(900, 3600)));
        when(valueOperations.getAndDelete(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.rotate("expired"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("refresh-token-invalid");
                })
                .verify();
    }

    @Test
    void aRefreshTokenCanBeUsedOnlyOnce() {
        when(parameterGroups.getServer()).thenReturn(Mono.just(server(900, 3600)));
        when(valueOperations.getAndDelete(anyString())).thenReturn(Mono.just("alice"), Mono.empty());
        when(reactiveUserDetailsService.findByUsername("alice")).thenReturn(Mono.just(enabledUser()));
        var pat = new PersonalAccessToken();
        when(userRoles.list("alice")).thenReturn(Flux.just("normal-role"));
        when(personalTokens.create(any(PersonalAccessToken.class), eq("alice"))).thenReturn(Mono.just(pat));
        when(personalTokens.generateToken(pat)).thenReturn(Mono.just("access-token"));
        when(personalTokens.deleteExpired("alice")).thenReturn(Mono.just(0L));
        when(valueOperations.set(anyString(), eq("alice"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.rotate("one-time")).expectNextCount(1).verifyComplete();
        StepVerifier.create(service.rotate("one-time")).expectError(ApiException.class).verify();
    }

    // 已禁用账户不能通过刷新令牌继续续期。
    @Test
    void disabledAccountCannotRotate() {
        when(parameterGroups.getServer()).thenReturn(Mono.just(server(900, 3600)));
        when(valueOperations.getAndDelete(anyString())).thenReturn(Mono.just("alice"));
        var disabled = User.withUsername("alice")
                .password("x")
                .authorities(List.of())
                .disabled(true)
                .build();
        when(reactiveUserDetailsService.findByUsername("alice")).thenReturn(Mono.just(disabled));

        StepVerifier.create(service.rotate("old-refresh-token"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("refresh-token-invalid");
                })
                .verify();
    }

    @Test
    void revokeAllDeletesOnlyTokensOwnedByTheUser() {
        var aliceKey = "pudans:refresh:hash-alice";
        var bobKey = "pudans:refresh:hash-bob";
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(Flux.just(aliceKey, bobKey));
        when(valueOperations.get(aliceKey)).thenReturn(Mono.just("alice"));
        when(valueOperations.get(bobKey)).thenReturn(Mono.just("bob"));
        when(redisTemplate.delete(aliceKey)).thenReturn(Mono.just(1L));

        StepVerifier.create(service.revokeAll("alice")).expectNext(1L).verifyComplete();

        verify(redisTemplate).delete(aliceKey);
        verify(redisTemplate, never()).delete(bobKey);
    }

    @Test
    void revokeAllReturnsZeroWhenTheUserHasNoRefreshToken() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(Flux.empty());

        StepVerifier.create(service.revokeAll("alice")).expectNext(0L).verifyComplete();
    }

    private static ParameterSetting.Server server(int accessTtl, int refreshTtl) {
        var server = new ParameterSetting.Server();
        server.setAccessTokenTtlSeconds(accessTtl);
        server.setRefreshTokenTtlSeconds(refreshTtl);
        return server;
    }
}
