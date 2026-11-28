package cn.tolim.pudans.iam.authentication.core.authentication;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.ArrayList;


public class IdentityProviderNotBindAuthentication extends AbstractAuthenticationToken
        implements java.io.Serializable {

    // Provider ID
    private final IdentityProviderUserDetails principal;

    public IdentityProviderNotBindAuthentication(IdentityProviderUserDetails principal) {
        super(new ArrayList<>());
        this.principal = principal;
    }

    // The credentials that prove the principal is correct. This is usually a password,
    // but could be anything relevant to the AuthenticationManager. Callers
    // are expected to populate the credentials.
    @Override
    public Object getCredentials() {
        return "";
    }

    // The identity of the principal being authenticated. In the case of an authentication
    // request with username and password, this would be the username. Callers are
    // expected to populate the principal for an authentication request.
    // The AuthenticationManager implementation will often return an
    // Authentication containing richer information as the principal for use by
    // the application. Many of the authentication providers will create a
    // UserDetails object as the principal.
    @Override
    public Object getPrincipal() {
        return principal;
    }
}
