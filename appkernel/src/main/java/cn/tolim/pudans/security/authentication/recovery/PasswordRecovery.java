package cn.tolim.pudans.security.authentication.recovery;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.Digests;
import java.security.SecureRandom;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PasswordRecovery {

    private static final String KEY_PREFIX = "pudans:password-reset:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Accounts accounts;
    private final PasswordResetMailer mailer;
    private final PudansProperties pudansProperties;

    // 按冷却窗口生成并投递验证码，异常时释放冷却。
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
                                    .flatMap(ignored -> mailer.sendCode(normalizedEmail, code))
                                    .then()))
                            .onErrorResume(error -> redisTemplate
                                    .delete(cooldownKey)
                                    .then(Mono.error(error)))
                            .then();
                });
    }

    // 校验验证码并限制失败次数，成功后更新密码。
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
                    if (!Digests.constantTimeEquals(storedDigest, digest(normalizedEmail, code))) {
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

    // 累计失败次数，达到上限后使验证码失效。
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

    // 统一邮箱格式，空值按空字符串处理。
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    // 使用安全随机源生成六位数字验证码。
    private static String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    // 摘要绑定邮箱与验证码，避免跨邮箱复用。
    private static String digest(String email, String code) {
        return Digests.sha256Hex(email + ":" + code);
    }

    // 对邮箱做摘要并截断，避免在缓存键中暴露原文。
    private static String emailKey(String normalizedEmail) {
        return Digests.sha256Hex(normalizedEmail).substring(0, 48);
    }

    // 拼接带前缀的缓存键，保证命名空间隔离。
    private static String key(String emailKey, String suffix) {
        return KEY_PREFIX + emailKey + ":" + suffix;
    }

    // 构造统一的验证码无效响应，避免泄露细节。
    private static ApiException invalidCode() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "recovery-code-invalid", "验证码无效或已过期。");
    }
}
