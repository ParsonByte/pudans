
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;


public class JwtLogoutAuthenticationToken extends AbstractAuthenticationToken {

    private final Authentication principal;

    @Getter
    private final JwtProtocolConfig config;

    @Getter
    private final String sessionId;

    @Getter
    private final String postLogoutRedirectUri;

    public JwtLogoutAuthenticationToken(Authentication principal, JwtProtocolConfig config,
                                        String postLogoutRedirectUri, String sessionId) {
        super(new ArrayList<>());
        this.principal = principal;
        this.config = config;
        this.sessionId = sessionId;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }


    @Override
    public Object getCredentials() {
        return "";
    }


    @Override
    public Object getPrincipal() {
        return principal;
    }


    public boolean isPrincipalAuthenticated() {
        return !AnonymousAuthenticationToken.class.isAssignableFrom(this.principal.getClass())
                && this.principal.isAuthenticated();
    }
}
