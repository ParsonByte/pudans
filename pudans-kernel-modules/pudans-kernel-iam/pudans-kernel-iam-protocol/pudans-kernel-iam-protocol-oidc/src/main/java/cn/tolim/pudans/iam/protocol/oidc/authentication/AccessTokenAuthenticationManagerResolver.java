package cn.tolim.pudans.iam.protocol.oidc.authentication;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ConfigurationSettingNames;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.util.Assert;


public class AccessTokenAuthenticationManagerResolver implements
        AuthenticationManagerResolver<HttpServletRequest> {

    private final Log logger = LogFactory.getLog(getClass());
    // JWT authentication provider
    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    // Opaque token authentication provider
    private final OpaqueTokenAuthenticationProvider opaqueTokenAuthenticationProvider;

    public AccessTokenAuthenticationManagerResolver(JwtAuthenticationProvider jwtAuthenticationProvider,
                                                    OpaqueTokenAuthenticationProvider opaqueTokenAuthenticationProvider) {
        Assert.notNull(jwtAuthenticationProvider, "jwtAuthenticationProvider 不能为空");
        Assert.notNull(opaqueTokenAuthenticationProvider,
                "opaqueTokenAuthenticationProvider 不能为空");
        this.jwtAuthenticationProvider = jwtAuthenticationProvider;
        this.opaqueTokenAuthenticationProvider = opaqueTokenAuthenticationProvider;
    }

    // Resolve an AuthenticationManager from a provided context
    // Parameter request HttpServletRequest
    // Returns the AuthenticationManager to use
    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        AuthorizationServerContext context = AuthorizationServerContextHolder.getContext();
        AuthorizationServerSettings settings = context.getAuthorizationServerSettings();
        String tokenFormat = settings
                .getSetting(ConfigurationSettingNames.Token.ACCESS_TOKEN_FORMAT);
        // Use the parser for the token format
        if (OAuth2TokenFormat.SELF_CONTAINED.getValue().equals(tokenFormat)) {
            return jwtAuthenticationProvider::authenticate;
        }
        if (OAuth2TokenFormat.REFERENCE.getValue().equals(tokenFormat)) {
            return opaqueTokenAuthenticationProvider::authenticate;
        }
        return null;
    }

}
