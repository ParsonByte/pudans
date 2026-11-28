package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.util.Assert;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class OAuth2AuthorizationImplicitAccessTokenAuthenticationToken extends
        AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 3310860855517523150L;

    private final RegisteredClient registeredClient;
    private final Authentication clientPrincipal;
    private final OAuth2AccessToken accessToken;

    private final Map<String, Object> additionalParameters;

    // Constructs an OAuth2AuthorizationImplicitAccessTokenAuthenticationToken using the provided parameters.
    // Parameter registeredClient the registered client
    // Parameter clientPrincipal the authenticated client principal
    // Parameter accessToken the access token
    public OAuth2AuthorizationImplicitAccessTokenAuthenticationToken(RegisteredClient registeredClient,
                                                                     Authentication clientPrincipal,
                                                                     OAuth2AccessToken accessToken) {
        this(registeredClient, clientPrincipal, accessToken, Collections.emptyMap());
    }

    // Constructs an OAuth2AuthorizationImplicitAccessTokenAuthenticationToken using the provided parameters.
    // Parameter registeredClient the registered client
    // Parameter clientPrincipal the authenticated client principal
    // Parameter accessToken the access token
    // Parameter additionalParameters the additional parameters
    public OAuth2AuthorizationImplicitAccessTokenAuthenticationToken(RegisteredClient registeredClient,
                                                                     Authentication clientPrincipal,
                                                                     OAuth2AccessToken accessToken,
                                                                     Map<String, Object> additionalParameters) {
        super(Collections.emptyList());
        Assert.notNull(registeredClient, "registeredClient 不能为空");
        Assert.notNull(clientPrincipal, "clientPrincipal 不能为空");
        Assert.notNull(additionalParameters, "additionalParameters 不能为空");
        this.registeredClient = registeredClient;
        this.clientPrincipal = clientPrincipal;
        this.accessToken = accessToken;
        this.additionalParameters = additionalParameters;
    }

    @Override
    public Object getPrincipal() {
        return this.clientPrincipal;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    // Returns the registered client.
    // Returns the RegisteredClient
    public RegisteredClient getRegisteredClient() {
        return this.registeredClient;
    }

    // Returns the access token.
    // Returns the OAuth2AccessToken
    public OAuth2AccessToken getAccessToken() {
        return this.accessToken;
    }

    // Returns the additional parameters.
    // Returns a Map of the additional parameters, may be empty
    public Map<String, Object> getAdditionalParameters() {
        return this.additionalParameters;
    }
}
