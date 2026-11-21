package cn.tolim.pudans.core.user;

import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.infra.api.ParameterSetting;
import cn.tolim.pudans.security.api.PersonalAccessToken;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.setting.ParameterGroups;
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
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RefreshTokens {

    private static final String KEY_PREFIX = "pudans:refresh:";

    private static final long SCAN_BATCH_SIZE = 200;

    private static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 1800;

    private static final long DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 2592000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final HexFormat HEX = HexFormat.of();

    private final ReactiveStringRedisTemplate redisTemplate;

    private final ParameterGroups parameterGroups;

    private final PersonalTokens personalTokens;

    private final Groups groups;

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
 //让指定用户的所有令牌全部失效，相当于把他从所有设备上强制退出登录，并返回删掉了多少个。
    public Mono<Long> revokeAll(String username) {
        var options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*")
                .count(SCAN_BATCH_SIZE)
                .build();
        return redisTemplate
                .scan(options)
                .flatMap(key -> redisTemplate
                        .opsForValue()
                        .get(key)
                        .filter(username::equals)
                        .flatMap(owner -> redisTemplate.delete(key)))
                .reduce(0L, Long::sum);
    }

    private Mono<String> issue(String username, Duration ttl) {
        return Mono.fromSupplier(RefreshTokens::randomToken)
                .flatMap(token -> redisTemplate
                        .opsForValue()
                        .set(key(token), username, ttl)
                        .thenReturn(token));
    }

    private Mono<String> issueAccessToken(String username, Duration ttl) {
        return groups.getRoleNamesByUsername(username).collectList().flatMap(roles -> {
            var request = new PersonalAccessToken();
            var metadata = new Metadata();
            metadata.setGenerateName("refresh-" + username + "-");
            request.setMetadata(metadata);
            request.getSpec().setName("refresh access token");
            request.getSpec().setRoles(roles);
            request.getSpec().setExpiresAt(Instant.now().plus(ttl));
            return personalTokens.create(request, username).flatMap(personalTokens::generateToken);
        });
    }

    private Mono<TtlSettings> settings() {
        return parameterGroups
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
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "refresh-token-invalid", "刷新令牌无效或已过期。");
    }

    private record TtlSettings(Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record RefreshResult(
            String accessToken, String refreshToken, long accessTokenTtlSeconds, long refreshTokenTtlSeconds) {}
}
