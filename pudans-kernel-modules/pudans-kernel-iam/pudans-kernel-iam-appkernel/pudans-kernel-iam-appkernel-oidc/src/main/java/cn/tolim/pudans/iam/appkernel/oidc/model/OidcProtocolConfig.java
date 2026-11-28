package cn.tolim.pudans.iam.appkernel.oidc.model;

import cn.tolim.pudans.iam.appkernel.core.AbstractProtocolConfig;
import com.nimbusds.jose.jwk.JWK;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.time.Duration;
import java.util.List;
import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class OidcProtocolConfig extends AbstractProtocolConfig {

    @Serial
    private static final long serialVersionUID = -3671812647788723766L;

    // Client authentication method
    private Set<String> clientAuthMethods;

    // Grant type
    private Set<String> authGrantTypes;

    // Response type
    private Set<String> responseTypes;

    // Redirect URIs
    private Set<String> redirectUris;

    // Logout redirect URIs
    private Set<String> postLogoutRedirectUris;

    // scopes
    private Set<String> grantScopes;

    // Requires authorization consent
    private Boolean requireAuthConsent;

    // PKCE required
    private Boolean requireProofKey;

    // Token endpoint authentication signing algorithm
    private String tokenEndpointAuthSigningAlgorithm;

    // Authorization code lifetime in authorization code mode
    private Duration authorizationCodeTimeToLive;

    // Device code lifetime in device mode
    private Duration deviceCodeTimeToLive;

    // Refresh Token lifetime (minutes)
    private Duration refreshTokenTimeToLive;

    // ID Token lifetime (minutes)
    private Duration idTokenTimeToLive;

    // Access Token lifetime (minutes)
    private Duration accessTokenTimeToLive;

    // ID Token signing algorithm
    private String idTokenSignatureAlgorithm;

    // Access Token format
    private String accessTokenFormat;

    // Reuse refresh token
    private Boolean reuseRefreshToken;

    // jwks
    private List<JWK> jwks;

    // Configured
    private Boolean configured;

    public Set<String> getRedirectUris() {
        return CollectionUtils.isEmpty(redirectUris) ? Set.of() : redirectUris;
    }

    public Set<String> getPostLogoutRedirectUris() {
        return CollectionUtils.isEmpty(postLogoutRedirectUris) ? Set.of() : postLogoutRedirectUris;
    }

    public Set<String> getGrantScopes() {
        return CollectionUtils.isEmpty(grantScopes) ? Set.of() : grantScopes;
    }

    public Set<String> getClientAuthMethods() {
        return CollectionUtils.isEmpty(clientAuthMethods) ? Set.of() : clientAuthMethods;
    }

    public Set<String> getResponseTypes() {
        return CollectionUtils.isEmpty(responseTypes) ? Set.of() : responseTypes;
    }

    public List<JWK> getJwks() {
        return CollectionUtils.isEmpty(jwks) ? List.of() : jwks;
    }
}
