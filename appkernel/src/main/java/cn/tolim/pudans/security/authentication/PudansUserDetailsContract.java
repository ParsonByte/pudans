package cn.tolim.pudans.security.authentication;

import org.springframework.security.core.userdetails.UserDetails;

public interface PudansUserDetailsContract extends UserDetails {


    // 标识该账户是否已启用双因素认证。
    boolean isTwoFactorAuthEnabled();


    // 返回双因素认证密钥的加密值，供验证器解密使用。
    String getTotpEncryptedSecret();
}
