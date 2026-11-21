package cn.tolim.pudans.security.authentication.oauth2;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.TWO_FACTOR_AUTHENTICATED_ROLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.security.authentication.PudansUserDetails;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OAuth2TwoFactorSupportTest {

    private static PudansUser twoFactorUser() {
        var details = new PudansUserDetails(
                "alice", "encoded", List.of(new SimpleGrantedAuthority("ROLE_normal-role")), false);
        return new PudansUser(details, true, "secret");
    }

    @Test
    void requiresTwoFactorOnlyUntilTheFactorAuthorityIsPresent() {
        var user = twoFactorUser();
        var withoutFactor = UsernamePasswordAuthenticationToken.authenticated(
                user, "", user.getAuthorities());
        var withFactor = UsernamePasswordAuthenticationToken.authenticated(
                user,
                "",
                List.of(
                        new SimpleGrantedAuthority("ROLE_normal-role"),
                        new SimpleGrantedAuthority(
                                ROLE_PREFIX + TWO_FACTOR_AUTHENTICATED_ROLE_NAME)));

        assertThat(OAuth2TwoFactorSupport.requiresTwoFactor(withoutFactor)).isTrue();
        assertThat(OAuth2TwoFactorSupport.requiresTwoFactor(withFactor)).isFalse();
    }

    @Test
    void pendingTwoFactorAuthenticationAlwaysRequiresTheChallenge() {
        var pending = new TwoFactorAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(
                twoFactorUser(), ""));

        assertThat(OAuth2TwoFactorSupport.requiresTwoFactor(pending)).isTrue();
    }

    @Test
    void absentPreviousAuthenticationIsNeverSatisfied() {
        assertThat(OAuth2TwoFactorSupport.twoFactorSatisfied(null, "alice")).isFalse();
        assertThat(OAuth2TwoFactorSupport.twoFactorSatisfied("no-authentication", "alice"))
                .isFalse();
    }

    @Test
    void previousAuthenticationWithTheFactorIsSatisfied() {
        var user = twoFactorUser();
        var withFactor = UsernamePasswordAuthenticationToken.authenticated(
                user,
                "",
                List.of(
                        new SimpleGrantedAuthority("ROLE_normal-role"),
                        new SimpleGrantedAuthority(
                                ROLE_PREFIX + TWO_FACTOR_AUTHENTICATED_ROLE_NAME)));

        assertThat(OAuth2TwoFactorSupport.twoFactorSatisfied(withFactor, "alice")).isTrue();
        assertThat(OAuth2TwoFactorSupport.twoFactorSatisfied(withFactor, "bob")).isFalse();
    }

    @Test
    void previousAuthenticationWithoutTheFactorIsNotSatisfied() {
        var user = twoFactorUser();
        var withoutFactor = UsernamePasswordAuthenticationToken.authenticated(
                user, "", user.getAuthorities());

        assertThat(OAuth2TwoFactorSupport.twoFactorSatisfied(withoutFactor, "alice"))
                .isFalse();
        assertThat(OAuth2TwoFactorSupport.twoFactorSatisfied(
                        new TwoFactorAuthentication(withoutFactor), "alice"))
                .isFalse();
    }

    // 令牌认证不能代替目标账号的双重验证。
    @Test
    void jwtAuthenticationIsNeverSatisfied() {
        var jwt = Jwt.withTokenValue("pat")
                .header("alg", "none")
                .claim("sub", "alice")
                .build();
        var authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_authenticated")), "alice");

        assertThat(OAuth2TwoFactorSupport.twoFactorSatisfied(authentication, "alice"))
                .isFalse();
    }
}
