package cn.tolim.pudans.security.authentication.twofactor.totp;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TotpTwoFactorVerifier {

    public static final String TYPE = "totp";

    private final TotpAuthService totpAuthService;

    public String getType() {
        return TYPE;
    }

    public Mono<Boolean> verify(User user, String code) {
        var spec = user.getSpec();
        var secret = spec == null ? null : spec.getTotpEncryptedSecret();
        if (StringUtils.isBlank(secret)) {
            return Mono.error(new TwoFactorAuthException("TOTP secret not configured."));
        }
        return Mono.fromCallable(() -> {
                    var parsed = Integer.parseInt(code == null ? "" : code.trim());
                    return totpAuthService.validateTotp(totpAuthService.decryptSecret(secret), parsed);
                })
                .onErrorMap(
                        NumberFormatException.class,
                        error -> new TwoFactorAuthException("Invalid TOTP code.", error));
    }
}
