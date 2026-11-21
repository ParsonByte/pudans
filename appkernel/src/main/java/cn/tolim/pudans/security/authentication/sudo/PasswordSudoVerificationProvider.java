package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

// 没有 TOTP 的用户用当前密码完成敏感操作确认，避免直接放行。
@Component
@Order(1)
@RequiredArgsConstructor
class PasswordSudoVerificationProvider implements SudoVerificationProvider {

    static final String METHOD = "password";

    private final Accounts accounts;

    // 使用稳定的密码确认方式名
    @Override
    public String method() {
        return METHOD;
    }

    // 密码确认不需要发送验证码
    @Override
    public boolean canSendCode() {
        return false;
    }

    // 只有已设置密码的账户才提供该方式
    @Override
    public Mono<Boolean> supports(User user) {
        return Mono.just(StringUtils.isNotBlank(user.getSpec().getPassword()));
    }

    // 密码方式没有可发送的验证码
    @Override
    public Mono<Void> sendCode(User user) {
        return Mono.error(new ServerWebInputException("密码确认无需发送验证码"));
    }

    // 校验当前密码，失败时统一返回安全确认失败
    @Override
    public Mono<Void> verify(User user, String code) {
        return accounts
                .confirmPassword(user.getMetadata().getName(), code)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(SudoVerificationFailedException::new))
                .then();
    }
}
