package cn.tolim.pudans.iam.authentication.core.authentication;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.io.Serial;
import java.util.ArrayList;


@Getter
public class IdentityProviderBindAuthentication extends AbstractAuthenticationToken
        implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = -1506897701981698420L;

    // Username supplied for identity provider binding
    private final String username;

    // Password supplied for identity provider binding
    private final String password;

    public IdentityProviderBindAuthentication(String username, String password) {
        super(new ArrayList<>());
        this.username = username;
        this.password = password;
    }

    // Returns empty credentials; the password is consumed by the authentication provider.
    @Override
    public Object getCredentials() {
        return "";
    }

    // Returns the username of the principal being bound.
    @Override
    public Object getPrincipal() {
        return username;
    }

}
