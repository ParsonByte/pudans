package cn.tolim.pudans.security.authentication.oauth2;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.TWO_FACTOR_AUTHENTICATED_ROLE_NAME;

import cn.tolim.pudans.security.contract.authentication.oauth2.PudansOAuth2AuthenticationToken;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class OAuth2TwoFactorSupport {

    private OAuth2TwoFactorSupport() {}

    // 判断当前身份是否仍缺少双重验证。
    static boolean requiresTwoFactor(Authentication authentication) {
        if (authentication instanceof TwoFactorAuthentication) {
            return true;
        }
        if (authentication instanceof PudansOAuth2AuthenticationToken token
                && token.getUserDetails() instanceof PudansUserDetailsContract contract) {
            return contract.isTwoFactorAuthEnabled() && !hasFactor(authentication);
        }
        return authentication.getPrincipal() instanceof PudansUserDetailsContract contract
                && contract.isTwoFactorAuthEnabled()
                && !hasFactor(authentication);
    }

    // 只有同一用户且已完成双重验证的前置登录，才能满足目标账号的双重验证。
    static boolean twoFactorSatisfied(Object previousAuthentication, String targetUsername) {
        return previousAuthentication instanceof Authentication previous
                && previous.isAuthenticated()
                && !(previous instanceof JwtAuthenticationToken)
                && previous.getName().equals(targetUsername)
                && !requiresTwoFactor(previous);
    }

    // 以双因素角色标识确认验证已经完成。
    static boolean hasFactor(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> (ROLE_PREFIX + TWO_FACTOR_AUTHENTICATED_ROLE_NAME)
                        .equals(authority.getAuthority()));
    }
}
