package cn.tolim.pudans.core.security.authentication.recovery;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.PudansProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordRecovery {

    private static final String KEY_PREFIX = "pudans:password-reset:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Accounts accounts;
    private final PasswordResetMailer mailer;
    private final PudansProperties pudansProperties;

    public Mono<Void> sendCode(String email) {
        var normalizedEmail = normalizeEmail(email);
        var options = pudansProperties.getSecurity().getPasswordReset();
        var emailKey = emailKey(normalizedEmail);
        var cooldownKey = key(emailKey, "cooldown");
        var codeKey = key(emailKey, "code");

        return redisTemplate
                .opsForValue()
                .setIfAbsent(cooldownKey, "1", options.getResendCooldown())
                .flatMap(acquired -> {
                    if (!Boolean.TRUE.equals(acquired)) {
                        return Mono.error(new ApiException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "recovery-code-cooldown",
                                "请稍后再请求新的验证码。"));
                    }

                    var code = generateCode();
                    var digest = digest(normalizedEmail, code);
                    return redisTemplate
                            .opsForValue()
                            .set(codeKey, digest, options.getCodeTtl())
                            .then(Mono.defer(() -> accounts
                                    .listByEmail(normalizedEmail)
                                    .next()
                                    .flatMap(user -> mailer.sendCode(normalizedEmail, code))
                                    .then()))
                            .onErrorResume(error -> redisTemplate
                                    .delete(cooldownKey)
                                    .then(Mono.error(error)))
                            .then();
                });
    }

    public Mono<Void> resetPassword(String email, String code, String newPassword) {
        var normalizedEmail = normalizeEmail(email);
        var options = pudansProperties.getSecurity().getPasswordReset();
        var emailKey = emailKey(normalizedEmail);
        var codeKey = key(emailKey, "code");
        var attemptsKey = key(emailKey, "attempts");

        return redisTemplate
                .opsForValue()
                .get(codeKey)
                .switchIfEmpty(Mono.error(invalidCode()))
                .flatMap(storedDigest -> {
                    if (!constantTimeEquals(storedDigest, digest(normalizedEmail, code))) {
                        return registerFailedAttempt(attemptsKey, codeKey, options.getMaxAttempts())
                                .then(Mono.error(invalidCode()));
                    }

                    return accounts
                            .listByEmail(normalizedEmail)
                            .next()
                            .switchIfEmpty(Mono.error(invalidCode()))
                            .flatMap(user -> accounts
                                    .updateWithRawPassword(user.getMetadata().getName(), newPassword))
                            .then(Mono.defer(() -> redisTemplate
                                    .delete(codeKey)
                                    .then(redisTemplate.delete(attemptsKey))))
                            .then();
                });
    }

    private Mono<Void> registerFailedAttempt(String attemptsKey, String codeKey, int maxAttempts) {
        return redisTemplate
                .opsForValue()
                .increment(attemptsKey)
                .flatMap(attempts -> {
                    var expire = attempts == 1
                            ? redisTemplate.expire(attemptsKey, java.time.Duration.ofMinutes(10))
                            : Mono.just(true);
                    return expire.flatMap(ignored -> attempts >= maxAttempts
                            ? redisTemplate.delete(codeKey).then()
                            : Mono.empty());
                })
                .then();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private static String digest(String email, String code) {
        return sha256(email + ":" + code);
    }

    private static String emailKey(String normalizedEmail) {
        return sha256(normalizedEmail).substring(0, 48);
    }

    private static String key(String emailKey, String suffix) {
        return KEY_PREFIX + emailKey + ":" + suffix;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static ApiException invalidCode() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "recovery-code-invalid", "验证码无效或已过期。");
    }
}
