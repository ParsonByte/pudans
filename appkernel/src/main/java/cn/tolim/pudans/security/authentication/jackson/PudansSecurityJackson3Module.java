package cn.tolim.pudans.security.authentication.jackson;

import cn.tolim.pudans.contract.security.authentication.oauth2.PudansOAuth2AuthenticationToken;
import cn.tolim.pudans.security.authentication.PudansUserDetails;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import tools.jackson.databind.module.SimpleModule;

public class PudansSecurityJackson3Module extends SimpleModule {

    @Override
    public void setupModule(SetupContext context) {
        context.setMixIn(PudansUser.class, PudansUserMixin.class);
        context.setMixIn(PudansUserDetails.class, PudansUserDetailsMixin.class);
        context.setMixIn(TwoFactorAuthentication.class, TwoFactorAuthenticationMixin.class);
        context.setMixIn(PudansOAuth2AuthenticationToken.class, PudansOAuth2AuthenticationTokenMixin.class);
        context.setMixIn(SwitchUserGrantedAuthority.class, SwitchUserGrantedAuthorityMixIn.class);
    }
}
