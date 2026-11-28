package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;

import java.io.Serial;
import java.util.*;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class OAuth2AuthorizationImplicitRequestAuthenticationToken extends
        AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = -5662861007848991326L;

    private final String authorizationUri;
    private final String clientId;
    private final Authentication principal;
    private final String redirectUri;
    private final String state;
    private final Set<String> scopes;

    private final Set<String> responseTypes;
    private final String responseMode;
    private final Map<String, Object> additionalParameters;

    // Constructs an OAuth2AuthorizationCodeRequestAuthenticationToken using the provided parameters.
    // Parameter authorizationUri the authorization URI
    // Parameter clientId the client identifier
    // Parameter principal the Principal (Resource Owner)
    // Parameter redirectUri the redirect uri
    // Parameter state the state
    // Parameter scopes the requested scope(s)
    // Parameter responseTypes the response type(s)
    // Parameter responseMode the response mode
    // Parameter additionalParameters the additional parameters
    public OAuth2AuthorizationImplicitRequestAuthenticationToken(String authorizationUri,
                                                                 String clientId,
                                                                 Authentication principal,
                                                                 @Nullable String redirectUri,
                                                                 @Nullable String state,
                                                                 @Nullable Set<String> scopes,
                                                                 Set<String> responseTypes,
                                                                 String responseMode,
                                                                 @Nullable Map<String, Object> additionalParameters) {
        super(Collections.emptyList());
        Assert.hasText(authorizationUri, "authorizationUri 不能为空");
        Assert.hasText(clientId, "clientId 不能为空");
        Assert.notNull(principal, "principal 不能为空");
        this.authorizationUri = authorizationUri;
        this.clientId = clientId;
        this.principal = principal;
        this.redirectUri = redirectUri;
        this.state = state;
        this.scopes = Collections
                .unmodifiableSet(scopes != null ? new HashSet<>(scopes) : Collections.emptySet());
        this.responseTypes = Collections.unmodifiableSet(
                scopes != null ? new HashSet<>(responseTypes) : Collections.emptySet());
        this.responseMode = responseMode;
        this.additionalParameters = Collections
                .unmodifiableMap(additionalParameters != null ? new HashMap<>(additionalParameters)
                        : Collections.emptyMap());
    }

    // Constructs an OAuth2AuthorizationCodeRequestAuthenticationToken using the provided parameters.
    // Parameter authorizationUri the authorization URI
    // Parameter clientId the client identifier
    // Parameter principal the Principal (Resource Owner)
    // Parameter redirectUri the redirect uri
    // Parameter state the state
    // Parameter scopes the authorized scope(s)
    // Parameter responseTypes the response type(s)
    public OAuth2AuthorizationImplicitRequestAuthenticationToken(String authorizationUri,
                                                                 String clientId,
                                                                 Authentication principal,
                                                                 @Nullable String redirectUri,
                                                                 @Nullable String state,
                                                                 @Nullable Set<String> scopes,
                                                                 Set<String> responseTypes,
                                                                 String responseMode) {
        super(Collections.emptyList());
        Assert.hasText(authorizationUri, "authorizationUri 不能为空");
        Assert.hasText(clientId, "clientId 不能为空");
        Assert.notNull(principal, "principal 不能为空");
        this.authorizationUri = authorizationUri;
        this.clientId = clientId;
        this.principal = principal;
        this.redirectUri = redirectUri;
        this.state = state;
        this.scopes = Collections
                .unmodifiableSet(scopes != null ? new HashSet<>(scopes) : Collections.emptySet());
        this.responseTypes = Collections.unmodifiableSet(
                scopes != null ? new HashSet<>(responseTypes) : Collections.emptySet());
        this.responseMode = responseMode;
        this.additionalParameters = Collections.emptyMap();
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    // Returns the authorization URI.
    // Returns the authorization URI
    public String getAuthorizationUri() {
        return this.authorizationUri;
    }

    // Returns the client identifier.
    // Returns the client identifier
    public String getClientId() {
        return this.clientId;
    }

    // Returns the redirect uri.
    // Returns the redirect uri
    @Nullable
    public String getRedirectUri() {
        return this.redirectUri;
    }

    // Returns the state.
    // Returns the state
    @Nullable
    public String getState() {
        return this.state;
    }

    // Returns the requested (or authorized) scope(s).
    // Returns the requested (or authorized) scope(s), or an empty Set if not available
    public Set<String> getScopes() {
        return this.scopes;
    }

    // Returns the additional parameters.
    // Returns the additional parameters, or an empty Map if not available
    public Map<String, Object> getAdditionalParameters() {
        return this.additionalParameters;
    }

    // Returns the response types.
    // Returns the requested (or response) type(s), or an empty Set if not available
    public Set<String> getResponseTypes() {
        return responseTypes;
    }

    public String getResponseMode() {
        return responseMode;
    }
}
