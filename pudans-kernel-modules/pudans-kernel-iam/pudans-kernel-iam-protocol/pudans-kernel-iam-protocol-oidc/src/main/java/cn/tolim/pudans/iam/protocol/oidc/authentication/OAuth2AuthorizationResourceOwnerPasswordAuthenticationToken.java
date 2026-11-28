package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken extends
        OAuth2AuthorizationGrantAuthenticationToken {

    // Grant type: password mode
    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");

    private final String username;
    private final String password;
    private final Set<String> scopes;

    // Constructs
    // Parameter username username
    // Parameter password password
    // Parameter scopes scopes
    // Parameter clientPrincipal the authenticated client principal
    // Parameter additionalParameters the additional parameters
    public OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken(String username,
                                                                       @Nullable String password,
                                                                       Set<String> scopes,
                                                                       Authentication clientPrincipal,
                                                                       @Nullable Map<String, Object> additionalParameters) {
        super(PASSWORD, clientPrincipal, additionalParameters);
        Assert.hasText(username, "用户名不能为空");
        Assert.hasText(password, "密码不能为空");
        this.username = username;
        this.password = password;
        this.scopes = Collections
                .unmodifiableSet(scopes != null ? new HashSet<>(scopes) : Collections.emptySet());
    }

    // Returns the username
    // Returns the username
    public String getUsername() {
        return this.username;
    }

    // Returns the password.
    // Returns the password
    @Nullable
    public String getPassword() {
        return this.password;
    }

    // getScopes
    // Returns scopes
    public Set<String> getScopes() {
        return scopes;
    }

    public Object getClientPrincipal() {
        return super.getPrincipal();
    }
}
