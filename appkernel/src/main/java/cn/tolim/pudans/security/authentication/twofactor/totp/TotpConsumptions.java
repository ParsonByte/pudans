package cn.tolim.pudans.security.authentication.twofactor.totp;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class TotpConsumptions {

    private static final int MAX_ATTEMPTS = 3;

    private final ReactiveExtensionClient client;

    private final TotpSecrets totpSecrets;

    private Clock clock = Clock.systemUTC();

    // 供测试替换时钟，控制验证码时间步的边界行为。
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // 校验并消费验证码，乐观锁重试防止并发重放。
    public Mono<User> consume(User user, int code) {
        var username = user.getMetadata().getName();
        return Mono.defer(() -> client.fetch(User.class, username)
                        .switchIfEmpty(Mono.error(() -> new TwoFactorAuthException("用户不存在。")))
                        .flatMap(current -> {
                            var spec = current.getSpec();
                            var encryptedSecret = spec == null ? null : spec.getTotpEncryptedSecret();
                            if (StringUtils.isBlank(encryptedSecret)) {
                                return Mono.error(new TwoFactorAuthException("未配置 TOTP 密钥。"));
                            }
                            var step = totpSecrets.matchStep(
                                    totpSecrets.decryptSecret(encryptedSecret), code, clock.millis());
                            if (step == null) {
                                return Mono.error(new TwoFactorAuthException("TOTP 验证码无效。"));
                            }
                            if (spec.getTotpLastStep() != null && spec.getTotpLastStep() >= step) {
                                return Mono.error(new TwoFactorAuthException("TOTP 验证码已使用。"));
                            }
                            spec.setTotpLastStep(step);
                            return client.update(current);
                        }))
                .retryWhen(Retry.max(MAX_ATTEMPTS - 1)
                        .filter(OptimisticLockingFailureException.class::isInstance))
                .onErrorResume(
                        OptimisticLockingFailureException.class,
                        error -> Mono.error(new TwoFactorAuthException("TOTP 验证码已使用。", error)));
    }

    // 绑定阶段仅校验验证码有效性，不记录已用步长。
    public long matchEnrollmentStep(String rawSecret, int code) {
        var step = totpSecrets.matchStep(rawSecret, code, clock.millis());
        if (step == null) {
            throw new TwoFactorAuthException("TOTP 验证码无效。");
        }
        return step;
    }
}
