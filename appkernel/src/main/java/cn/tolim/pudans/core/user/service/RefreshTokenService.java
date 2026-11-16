package cn.tolim.pudans.core.user.service;

import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "pudans:refresh:";

    private static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 1800;

    private static final long DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 2592000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final HexFormat HEX = HexFormat.of();

    private final ReactiveStringRedisTemplate redisTemplate;

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final PatService patService;

    private final UserGroupService userGroupService;

    public Mono<String> issue(String username) {
        return settings().flatMap(settings -> issue(username, settings.refreshTokenTtl()));
    }

    public Mono<RefreshResult> rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.error(invalidToken());
        }
        return settings().flatMap(settings -> redisTemplate
                .opsForValue()
                .getAndDelete(key(refreshToken))
                .switchIfEmpty(Mono.error(invalidToken()))
                .flatMap(username -> issueAccessToken(username, settings.accessTokenTtl())
                        .flatMap(accessToken -> issue(username, settings.refreshTokenTtl())
                                .map(newRefreshToken -> new RefreshResult(
                                        accessToken,
                                        newRefreshToken,
                                        settings.accessTokenTtl().toSeconds(),
                                        settings.refreshTokenTtl().toSeconds())))));
    }

    private Mono<String> issue(String username, Duration ttl) {
        return Mono.fromSupplier(RefreshTokenService::randomToken)
                .flatMap(token -> redisTemplate
                        .opsForValue()
                        .set(key(token), username, ttl)
                        .thenReturn(token));
    }

    private Mono<String> issueAccessToken(String username, Duration ttl) {
        return userGroupService.getRoleNamesByUsername(username).collectList().flatMap(roles -> {
            var request = new PersonalAccessToken();
            var metadata = new Metadata();
            metadata.setGenerateName("refresh-" + username + "-");
            request.setMetadata(metadata);
            request.getSpec().setName("refresh access token");
            request.getSpec().setRoles(roles);
            request.getSpec().setExpiresAt(Instant.now().plus(ttl));
            return patService.create(request, username).flatMap(patService::generateToken);
        });
    }

    private Mono<TtlSettings> settings() {
        return parameterSettingGroupService
                .getServer()
                .defaultIfEmpty(new ParameterSetting.Server())
                .map(server -> new TtlSettings(
                        ttl(server.getAccessTokenTtlSeconds(), DEFAULT_ACCESS_TOKEN_TTL_SECONDS),
                        ttl(server.getRefreshTokenTtlSeconds(), DEFAULT_REFRESH_TOKEN_TTL_SECONDS)));
    }

    private static Duration ttl(Integer seconds, long fallback) {
        var value = seconds == null || seconds <= 0 ? fallback : seconds;
        return Duration.ofSeconds(Math.max(60, value));
    }

    private static String randomToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    private static String sha256(String value) {
        try {
            return HEX.formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "refresh-token-invalid", "The refresh token is invalid or expired.");
    }

    private record TtlSettings(Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record RefreshResult(
            String accessToken, String refreshToken, long accessTokenTtlSeconds, long refreshTokenTtlSeconds) {}
}
