package cn.tolim.pudans.security.authentication.twofactor;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ANONYMOUS_ROLE_NAME;

import java.io.Serializable;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;


public class TwoFactorAuthentication extends AbstractAuthenticationToken implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Authentication previous;


    public TwoFactorAuthentication(Authentication previous) {
        super(List.of(new SimpleGrantedAuthority(ANONYMOUS_ROLE_NAME)));
        this.previous = previous;
    }

    @Override
    public Object getCredentials() {
        return previous.getCredentials();
    }

    @Override
    public Object getPrincipal() {
        return previous.getPrincipal();
    }

    @Override
    public boolean isAuthenticated() {

        return false;
    }

    public Authentication getPrevious() {
        return previous;
    }
}
