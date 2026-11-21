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

    // 构造完整用户信息，并复制权限集合防止外部修改。
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

    // 以禁用标记简化构造，其余账户状态默认可用。
    public PudansUserDetails(
            String username, String password, Collection<? extends GrantedAuthority> authorities, boolean disabled) {
        this(username, password, authorities, true, true, true, !disabled);
    }

    // 认证完成后清除密码，降低凭据在内存中泄露的风险。
    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
