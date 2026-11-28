package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

// This exception is thrown by OAuth2AuthorizationResourceOwnerPasswordAuthenticationProvider
// when an attempt to authenticate the OAuth 2.0 Authorization Request (or Consent) fails.
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class OAuth2AuthorizationResourceOwnerPasswordAuthenticationException extends
        OAuth2AuthenticationException {
    private final OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authorizationPasswordAuthenticationToken;

    // Constructs an OAuth2AuthorizationPasswordAuthenticationException using the provided parameters.
    // Parameter error the OAuth 2.0 Error
    // Parameter authorizationPasswordAuthenticationToken the Authentication instance of the OAuth 2.0 Authorization Request (or Consent)
    public OAuth2AuthorizationResourceOwnerPasswordAuthenticationException(OAuth2Error error,
                                                                           @Nullable OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authorizationPasswordAuthenticationToken) {
        super(error);
        this.authorizationPasswordAuthenticationToken = authorizationPasswordAuthenticationToken;
    }

    // Constructs an OAuth2AuthorizationPasswordAuthenticationException using the provided parameters.
    // Parameter error the OAuth 2.0 Error
    // Parameter cause the root cause
    // Parameter authorizationCodeRequestAuthentication the Authentication instance of the OAuth 2.0 Authorization Request (or Consent)
    public OAuth2AuthorizationResourceOwnerPasswordAuthenticationException(OAuth2Error error,
                                                                           Throwable cause,
                                                                           @Nullable OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken authorizationCodeRequestAuthentication) {
        super(error, cause);
        this.authorizationPasswordAuthenticationToken = authorizationCodeRequestAuthentication;
    }

    // Returns the Authentication instance of the OAuth 2.0 Authorization Request (or Consent), or null if not available.
    // Returns the OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken
    @Nullable
    public OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken getAuthorizationCodeRequestAuthentication() {
        return this.authorizationPasswordAuthenticationToken;
    }

}
