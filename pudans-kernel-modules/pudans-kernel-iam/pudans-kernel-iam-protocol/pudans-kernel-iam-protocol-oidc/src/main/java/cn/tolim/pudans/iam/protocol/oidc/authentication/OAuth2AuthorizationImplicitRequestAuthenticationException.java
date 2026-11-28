package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class OAuth2AuthorizationImplicitRequestAuthenticationException extends
        OAuth2AuthenticationException {
    private final OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthenticationToken;

    public OAuth2AuthorizationImplicitRequestAuthenticationException(OAuth2Error error,
                                                                     @Nullable OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthenticationToken) {
        super(error);
        this.authorizationImplicitRequestAuthenticationToken = authorizationImplicitRequestAuthenticationToken;
    }

    public OAuth2AuthorizationImplicitRequestAuthenticationException(OAuth2Error error,
                                                                     Throwable cause,
                                                                     @Nullable OAuth2AuthorizationImplicitRequestAuthenticationToken authorizationImplicitRequestAuthenticationToken) {
        super(error, cause);
        this.authorizationImplicitRequestAuthenticationToken = authorizationImplicitRequestAuthenticationToken;
    }

    @Nullable
    public OAuth2AuthorizationImplicitRequestAuthenticationToken getAuthorizationImplicitRequestAuthenticationToken() {
        return this.authorizationImplicitRequestAuthenticationToken;
    }

}
