package cn.tolim.pudans.security.authentication.twofactor;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class TwoFactorAuthenticationToken extends AbstractAuthenticationToken {

    private final String type;

    private final String code;

    public TwoFactorAuthenticationToken(String type, String code) {
        super(List.of());
        this.type = type;
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    @Override
    public Object getCredentials() {
        return code;
    }

    @Override
    public Object getPrincipal() {
        return code;
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }
}
