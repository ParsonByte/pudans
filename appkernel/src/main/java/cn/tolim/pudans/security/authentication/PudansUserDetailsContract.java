package cn.tolim.pudans.security.authentication;

import org.springframework.security.core.userdetails.UserDetails;

public interface PudansUserDetailsContract extends UserDetails {


    boolean isTwoFactorAuthEnabled();


    String getTotpEncryptedSecret();
}
