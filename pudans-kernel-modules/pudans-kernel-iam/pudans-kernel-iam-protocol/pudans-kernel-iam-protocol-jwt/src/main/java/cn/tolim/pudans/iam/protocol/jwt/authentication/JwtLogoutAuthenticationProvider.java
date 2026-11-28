
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtAuthenticationException;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.JWT_ERROR_URI;
import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.POST_LOGOUT_REDIRECT_URI;
import static cn.tolim.pudans.iam.protocol.jwt.exception.JwtErrorCodes.SERVER_ERROR;


public final class JwtLogoutAuthenticationProvider implements AuthenticationProvider {
    private final Logger logger = LoggerFactory.getLogger(JwtLogoutAuthenticationProvider.class);

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof JwtLogoutAuthenticationToken logoutAuthenticationToken)) {
            throw logoutError();
        }
        JwtProtocolConfig config = logoutAuthenticationToken.getConfig();
        if (config == null) {
            throw logoutError();
        }

        String postLogoutRedirectUri = logoutAuthenticationToken.getPostLogoutRedirectUri();
        String configuredPostLogoutRedirectUri = config.getPostLogoutRedirectUri();
        if (StringUtils.isAnyBlank(postLogoutRedirectUri, configuredPostLogoutRedirectUri)) {
            logger.info("JWT 退出：post_logout_redirect_uri 或配置的重定向地址为空。");
            throw logoutError();
        }

        if (!StringUtils.equals(postLogoutRedirectUri, configuredPostLogoutRedirectUri)) {
            logger.info(String.format(
                    "JWT 退出：post_logout_redirect_uri %s 与提供的 post_logout_redirect_uri %s 不匹配。",
                    postLogoutRedirectUri, configuredPostLogoutRedirectUri));
            throw logoutError();
        }

        logoutAuthenticationToken.setAuthenticated(true);
        return logoutAuthenticationToken;
    }

    private JwtAuthenticationException logoutError() {
        JwtError error = new JwtError(SERVER_ERROR,
                "JWT 退出请求参数：" + POST_LOGOUT_REDIRECT_URI, JWT_ERROR_URI);
        return new JwtAuthenticationException(error);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return JwtLogoutAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
