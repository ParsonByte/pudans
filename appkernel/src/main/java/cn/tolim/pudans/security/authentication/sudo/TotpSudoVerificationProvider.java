package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.security.authentication.twofactor.totp.TotpConsumptions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;


@Component
@Order(0)
class TotpSudoVerificationProvider implements SudoVerificationProvider {

    static final String METHOD = "totp";

    private final TotpConsumptions totpConsumptions;

    // 注入动态口令消费校验组件
    TotpSudoVerificationProvider(TotpConsumptions totpConsumptions) {
        this.totpConsumptions = totpConsumptions;
    }

    // 使用稳定的动态口令方式名
    @Override
    public String method() {
        return METHOD;
    }

    // 动态口令由本地生成，无需发送
    @Override
    public boolean canSendCode() {
        return false;
    }

    // 仅已绑定动态口令的账户可用
    @Override
    public Mono<Boolean> supports(User user) {
        return Mono.just(StringUtils.isNotBlank(user.getSpec().getTotpEncryptedSecret()));
    }

    // 动态口令方式不支持发送验证码
    @Override
    public Mono<Void> sendCode(User user) {
        return Mono.error(new ServerWebInputException("TOTP 无需发送验证码"));
    }

    // 校验动态口令并拒绝重复使用
    @Override
    public Mono<Void> verify(User user, String code) {
        var encryptedSecret = user.getSpec().getTotpEncryptedSecret();
        if (StringUtils.isBlank(encryptedSecret) || StringUtils.isBlank(code)) {
            return Mono.error(SudoVerificationFailedException::new);
        }
        int totpCode;
        try {
            totpCode = Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return Mono.error(SudoVerificationFailedException::new);
        }
        return totpConsumptions
                .consume(user, totpCode)
                .then()
                .onErrorMap(
                        cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException.class,
                        error -> new SudoVerificationFailedException());
    }
}
