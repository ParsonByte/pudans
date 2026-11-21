package cn.tolim.pudans.security.authentication;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


@Getter
public class PudansUserDetails implements UserDetails, CredentialsContainer, Serializable {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final boolean enabled;
    private String password;

    public PudansUserDetails(
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean accountNonExpired,
            boolean accountNonLocked,
            boolean credentialsNonExpired,
            boolean enabled) {
        this.username = username;
        this.password = password;
        this.authorities = List.copyOf(authorities);
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.enabled = enabled;
    }

    public PudansUserDetails(
            String username, String password, Collection<? extends GrantedAuthority> authorities, boolean disabled) {
        this(username, password, authorities, true, true, true, !disabled);
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
