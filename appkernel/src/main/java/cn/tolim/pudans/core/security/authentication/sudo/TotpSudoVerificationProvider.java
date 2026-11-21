package cn.tolim.pudans.core.security.authentication.sudo;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.security.authentication.twofactor.totp.TotpConsumptions;
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

    TotpSudoVerificationProvider(TotpConsumptions totpConsumptions) {
        this.totpConsumptions = totpConsumptions;
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public boolean canSendCode() {
        return false;
    }

    @Override
    public Mono<Boolean> supports(User user) {
        return Mono.just(StringUtils.isNotBlank(user.getSpec().getTotpEncryptedSecret()));
    }

    @Override
    public Mono<Void> sendCode(User user) {
        return Mono.error(new ServerWebInputException("TOTP 无需发送验证码"));
    }

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
                        cn.tolim.pudans.core.security.authentication.exception.TwoFactorAuthException.class,
                        error -> new SudoVerificationFailedException());
    }
}
