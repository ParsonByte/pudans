package cn.tolim.pudans.iam.authentication.core.jackjson;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderNotBindAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import cn.tolim.pudans.iam.authentication.core.authentication.OtpAuthentication;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;


public class AuthenticationJacksonModule extends Module {

    // Method that returns a display that can be used by Jackson
    // for informational purposes, as well as in associating extensions with
    // module that provides them.
    @Override
    public String getModuleName() {
        return AuthenticationJacksonModule.class.getName();
    }

    // Method that returns version of this module. Can be used by Jackson for
    // informational purposes.
    @Override
    public Version version() {
        return new Version(1, 0, 0, null, null, null);
    }

    @Override
    public void setupModule(SetupContext context) {
        context.setMixInAnnotations(IdentityProviderAuthentication.class, IdentityProviderAuthenticationTokenMixin.class);
        context.setMixInAnnotations(IdentityProviderNotBindAuthentication.class, IdentityProviderNotBindAuthenticationTokenMixin.class);
        context.setMixInAnnotations(OtpAuthentication.class, OtpAuthenticationTokenMixin.class);
        context.setMixInAnnotations(IdentityProviderUserDetails.class, IdentityProviderUserDetailsMixin.class);
        context.setMixInAnnotations(IdentityProviderType.class, IdentityProviderTypeMixin.class);
    }

}
