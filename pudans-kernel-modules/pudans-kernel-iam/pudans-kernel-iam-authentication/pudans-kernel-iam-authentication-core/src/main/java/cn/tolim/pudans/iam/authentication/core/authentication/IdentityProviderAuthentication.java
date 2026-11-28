package cn.tolim.pudans.iam.authentication.core.authentication;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;


public class IdentityProviderAuthentication extends AbstractAuthenticationToken
        implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = -1506897701981698420L;

    // Authenticated principal
    private final Object principal;

    // Provider type
    @Getter
    private final IdentityProviderType providerType;

    // Provider ID
    @Getter
    private final String providerId;

    // Creates an authenticated identity provider token.
    public IdentityProviderAuthentication(Object principal, IdentityProviderType providerType,
                                          String providerId,
                                          Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.providerId = providerId;
        this.providerType = providerType;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return super.getAuthorities();
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
    }

}
