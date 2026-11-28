
package cn.tolim.pudans.iam.protocol.jwt.endpoint.authentication;

import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.protocol.jwt.authentication.JwtLogoutAuthenticationToken;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationConverter;

import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.POST_LOGOUT_REDIRECT_URI;
import static cn.tolim.pudans.iam.protocol.jwt.endpoint.JwtAuthenticationEndpointUtils.throwError;


public final class JwtLogoutAuthenticationConverter implements AuthenticationConverter {
    private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken(
            "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    @Override
    public Authentication convert(HttpServletRequest request) {

        if (request.getParameterValues(POST_LOGOUT_REDIRECT_URI).length != 1) {
            throwError(new JwtError(OAuth2ErrorCodes.INVALID_REQUEST,
                    "JWT 退出请求参数：" + POST_LOGOUT_REDIRECT_URI));
        }
        ApplicationContext context = ApplicationContextHolder.getApplicationContext();
        JwtProtocolConfig config = (JwtProtocolConfig) context.getConfig()
                .get(JwtProtocolConfig.class.getName());
        String postLogoutRedirectUri = request.getParameter(POST_LOGOUT_REDIRECT_URI);
        String sessionId = null;
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionId = session.getId();
        }

        Authentication principal = SecurityContextHolder.getContext().getAuthentication();
        if (principal == null) {
            principal = ANONYMOUS_AUTHENTICATION;
        }
        return new JwtLogoutAuthenticationToken(principal, config, postLogoutRedirectUri,
                sessionId);
    }

}
