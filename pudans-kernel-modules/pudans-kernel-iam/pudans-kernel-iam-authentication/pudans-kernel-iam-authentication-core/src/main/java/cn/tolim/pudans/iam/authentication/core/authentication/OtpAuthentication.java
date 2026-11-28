package cn.tolim.pudans.iam.authentication.core.authentication;

import com.google.common.collect.Lists;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;


public class OtpAuthentication extends AbstractAuthenticationToken implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = -1506897701981698420L;

    // Authenticated principal
    private final Object principal;

    // Recipient email address or phone number
    private final String recipient;

    // Verification type
    private final String type;

    // Creates an unauthenticated OTP authentication token.
    public OtpAuthentication(Object principal, String recipient, String type) {
        super(Lists.newArrayList());
        this.principal = principal;
        this.recipient = recipient;
        this.type = type;
        setAuthenticated(false);
    }

    // Creates an OTP authentication token with the supplied authentication state.
    public OtpAuthentication(Object principal, String recipient, String type,
                             boolean authenticated) {
        super(Lists.newArrayList());
        this.principal = principal;
        this.recipient = recipient;
        this.type = type;
        setAuthenticated(authenticated);
    }

    // Creates an authenticated OTP token with the supplied authorities.
    public OtpAuthentication(Object principal, String recipient, String type,
                             Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.recipient = recipient;
        this.principal = principal;
        this.type = type;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.recipient;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getType() {
        return type;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
    }
}
