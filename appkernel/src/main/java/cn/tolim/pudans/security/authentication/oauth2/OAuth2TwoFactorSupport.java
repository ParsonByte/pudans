package cn.tolim.pudans.security.authentication.oauth2;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.TWO_FACTOR_AUTHENTICATED_ROLE_NAME;

import cn.tolim.pudans.contract.security.authentication.oauth2.PudansOAuth2AuthenticationToken;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import org.springframework.security.core.Authentication;

final class OAuth2TwoFactorSupport {

    private OAuth2TwoFactorSupport() {}

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

    // 前置认证缺失（未登录）时不能视为已满足双重验证，需由目标用户的双重验证设置决定。
    static boolean twoFactorSatisfied(Object previousAuthentication) {
        return previousAuthentication instanceof Authentication previous && !requiresTwoFactor(previous);
    }

    static boolean hasFactor(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> (ROLE_PREFIX + TWO_FACTOR_AUTHENTICATED_ROLE_NAME)
                        .equals(authority.getAuthority()));
    }
}
