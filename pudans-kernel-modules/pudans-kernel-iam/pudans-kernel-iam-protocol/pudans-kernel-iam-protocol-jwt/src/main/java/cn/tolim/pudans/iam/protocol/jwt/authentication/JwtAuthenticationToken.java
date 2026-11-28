
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.protocol.jwt.token.IdToken;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.UUID;


public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    @Getter
    private final String id;


    private final Authentication principal;


    @Getter
    private final IdToken idToken;


    @Getter
    private final JwtProtocolConfig config;

    @Getter
    @Setter
    private String targetUrl;


    public JwtAuthenticationToken(Authentication principal, JwtProtocolConfig config,
                                  IdToken idToken) {
        super(new ArrayList<>());
        this.principal = principal;
        this.idToken = idToken;
        this.config = config;
        this.id = UUID.randomUUID().toString();
        setAuthenticated(true);
    }


    @Override
    public Object getCredentials() {
        return "";
    }


    @Override
    public Object getPrincipal() {
        return principal;
    }
}
