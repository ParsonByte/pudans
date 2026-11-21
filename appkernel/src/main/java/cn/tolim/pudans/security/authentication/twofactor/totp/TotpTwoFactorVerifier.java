package cn.tolim.pudans.security.authentication.twofactor.totp;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TotpTwoFactorVerifier {

    public static final String TYPE = "totp";

    private final TotpConsumptions totpConsumptions;

    // 返回验证器类型标识，用于匹配请求的验证方式。
    public String getType() {
        return TYPE;
    }

    // 解析验证码并委托消费器校验，异常统一按失败处理。
    public Mono<Boolean> verify(User user, String code) {
        var spec = user.getSpec();
        var secret = spec == null ? null : spec.getTotpEncryptedSecret();
        if (StringUtils.isBlank(secret)) {
            return Mono.error(new TwoFactorAuthException("未配置 TOTP 密钥。"));
        }
        return Mono.fromCallable(() -> Integer.parseInt(code == null ? "" : code.trim()))
                .flatMap(parsed -> totpConsumptions.consume(user, parsed))
                .thenReturn(true)
                .onErrorResume(TwoFactorAuthException.class, error -> Mono.just(false))
                .onErrorMap(
                        NumberFormatException.class,
                        error -> new TwoFactorAuthException("TOTP 验证码无效。", error));
    }
}
