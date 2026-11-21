package cn.tolim.pudans.security.authentication.twofactor;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class TwoFactorAuthenticationToken extends AbstractAuthenticationToken {

    private final String type;

    private final String code;

    // 保存待校验的验证方式与验证码，初始为未认证状态。
    public TwoFactorAuthenticationToken(String type, String code) {
        super(List.of());
        this.type = type;
        this.code = code;
    }

    // 返回验证方式，管理器据此选择对应的校验器。
    public String getType() {
        return type;
    }

    // 返回用户提交的验证码，交由 TOTP 校验器比对。
    public String getCode() {
        return code;
    }

    // 凭据即验证码，供认证管理器统一读取。
    @Override
    public Object getCredentials() {
        return code;
    }

    // 尚未绑定用户主体，暂以验证码作为主体返回。
    @Override
    public Object getPrincipal() {
        return code;
    }

    // 该令牌仅承载请求数据，认证结果由管理器另行签发。
    @Override
    public boolean isAuthenticated() {
        return false;
    }
}
