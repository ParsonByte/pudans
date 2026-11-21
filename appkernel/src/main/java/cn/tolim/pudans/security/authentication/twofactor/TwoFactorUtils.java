package cn.tolim.pudans.security.authentication.twofactor;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import cn.tolim.pudans.contract.user.resource.User;
import org.apache.commons.lang3.StringUtils;

public enum TwoFactorUtils {
    ;

    public static TwoFactorAuthSettings getTwoFactorAuthSettings(User user) {
        var spec = user.getSpec();
        var tfaEnabled = defaultIfNull(spec.getTwoFactorAuthEnabled(), false);
        var emailVerified = spec.isEmailVerified();
        var totpEncryptedSecret = spec.getTotpEncryptedSecret();
        var settings = new TwoFactorAuthSettings();
        settings.setEnabled(tfaEnabled);
        settings.setEmailVerified(emailVerified);
        settings.setTotpConfigured(StringUtils.isNotBlank(totpEncryptedSecret));
        return settings;
    }
}
