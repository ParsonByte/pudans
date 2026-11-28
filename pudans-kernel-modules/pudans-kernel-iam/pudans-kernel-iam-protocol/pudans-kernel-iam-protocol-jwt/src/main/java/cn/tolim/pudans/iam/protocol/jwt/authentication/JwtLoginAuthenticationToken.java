
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;


public class JwtLoginAuthenticationToken extends AbstractAuthenticationToken {

    private final Authentication principal;
    @Getter
    private final JwtProtocolConfig config;
    @Getter
    private String targetUrl;

    public JwtLoginAuthenticationToken(Authentication principal, String targetUrl,
                                       JwtProtocolConfig config) {
        super(new ArrayList<>());
        this.principal = principal;
        this.targetUrl = targetUrl;
        this.config = config;
    }


    @Override
    public Object getCredentials() {
        return "";
    }


    @Override
    public Object getPrincipal() {
        return principal;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

}
