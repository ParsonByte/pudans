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


    // 包装登录前的匿名认证，保留原主体与凭据供二次校验。
    public TwoFactorAuthentication(Authentication previous) {
        super(List.of(new SimpleGrantedAuthority(ANONYMOUS_ROLE_NAME)));
        this.previous = previous;
    }

    // 透传登录前凭据，供二次验证流程读取。
    @Override
    public Object getCredentials() {
        return previous.getCredentials();
    }

    // 透传已认证用户主体，保持身份上下文一致。
    @Override
    public Object getPrincipal() {
        return previous.getPrincipal();
    }

    // 未通过二次验证前始终视为未认证，防止越权访问。
    @Override
    public boolean isAuthenticated() {

        return false;
    }

    // 暴露登录前认证，供管理器完成校验后提升权限。
    public Authentication getPrevious() {
        return previous;
    }
}
