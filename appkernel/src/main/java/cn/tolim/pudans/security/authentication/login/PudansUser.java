package cn.tolim.pudans.security.authentication.login;

import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.Assert;

public class PudansUser implements PudansUserDetailsContract, CredentialsContainer, Serializable {

    private static final long serialVersionUID = 1L;

    private final UserDetails delegate;

    private final boolean twoFactorAuthEnabled;

    private String totpEncryptedSecret;

    private String refreshToken;

    // 包装用户详情并补充双因素与动态口令信息。
    public PudansUser(UserDetails delegate, boolean twoFactorAuthEnabled, String totpEncryptedSecret) {
        Assert.notNull(delegate, "被委托的用户不能为空");
        this.delegate = delegate;
        this.twoFactorAuthEnabled = twoFactorAuthEnabled;
        this.totpEncryptedSecret = totpEncryptedSecret;
    }

    // 权限来自被委托的用户详情。
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    // 密码由被委托的用户详情提供。
    @Override
    public String getPassword() {
        return delegate.getPassword();
    }

    // 用户名由被委托的用户详情提供。
    @Override
    public String getUsername() {
        return delegate.getUsername();
    }

    // 账号过期状态委托给内层用户详情。
    @Override
    public boolean isAccountNonExpired() {
        return delegate.isAccountNonExpired();
    }

    // 锁定状态委托给内层用户详情。
    @Override
    public boolean isAccountNonLocked() {
        return delegate.isAccountNonLocked();
    }

    // 凭据过期状态委托给内层用户详情。
    @Override
    public boolean isCredentialsNonExpired() {
        return delegate.isCredentialsNonExpired();
    }

    // 启用状态委托给内层用户详情。
    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    // 同时清除动态口令密钥，避免明文驻留内存。
    @Override
    public void eraseCredentials() {
        if (delegate instanceof CredentialsContainer container) {
            container.eraseCredentials();
        }
        this.totpEncryptedSecret = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PudansUser user) {
            return Objects.equals(this.delegate, user.delegate);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    // 标记登录后是否仍需完成双重验证。
    @Override
    public boolean isTwoFactorAuthEnabled() {
        return this.twoFactorAuthEnabled;
    }

    // 返回校验动态口令所需的加密密钥。
    @Override
    public String getTotpEncryptedSecret() {
        return this.totpEncryptedSecret;
    }

    // 返回会话续期使用的刷新令牌。
    public String getRefreshToken() {
        return refreshToken;
    }

    // 登录成功后写入新签发的刷新令牌。
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public static class Builder {

        private final UserDetails user;

        private boolean twoFactorAuthEnabled;

        private String totpEncryptedSecret;

        // 以用户详情为起点构建扩展用户对象。
        public Builder(UserDetails user) {
            this.user = user;
        }

        // 声明该用户登录后需要完成双重验证。
        public Builder twoFactorAuthEnabled(boolean twoFactorAuthEnabled) {
            this.twoFactorAuthEnabled = twoFactorAuthEnabled;
            return this;
        }

        // 携带双重验证所需的加密动态口令密钥。
        public Builder totpEncryptedSecret(String totpEncryptedSecret) {
            this.totpEncryptedSecret = totpEncryptedSecret;
            return this;
        }

        // 产出带扩展信息的用户详情契约。
        public PudansUserDetailsContract build() {
            return new PudansUser(user, twoFactorAuthEnabled, totpEncryptedSecret);
        }
    }
}
