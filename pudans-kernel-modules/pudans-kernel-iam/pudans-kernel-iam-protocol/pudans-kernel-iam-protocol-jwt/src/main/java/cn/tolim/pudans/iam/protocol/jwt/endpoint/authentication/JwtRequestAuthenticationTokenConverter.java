
package cn.tolim.pudans.iam.protocol.jwt.endpoint.authentication;

import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.protocol.jwt.authentication.JwtLoginAuthenticationToken;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtAuthenticationException;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.MapUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;

import static cn.tolim.pudans.iam.infra.util.HttpRequestUtils.getFormParameters;
import static cn.tolim.pudans.iam.infra.util.HttpRequestUtils.getQueryParameters;
import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.JWT_ERROR_URI;
import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.TARGET_URL;
import static org.springframework.http.HttpMethod.GET;


public final class JwtRequestAuthenticationTokenConverter implements AuthenticationConverter {
    private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken(
            "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    @Override
    public Authentication convert(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = GET.name().equals(request.getMethod())
                ? getQueryParameters(request)
                : getFormParameters(request);
        String targetUrl = parameters.getFirst(TARGET_URL);

        Authentication principal = SecurityContextHolder.getContext().getAuthentication();
        if (principal == null) {
            principal = ANONYMOUS_AUTHENTICATION;
        }
        ApplicationContext applicationContext = ApplicationContextHolder.getApplicationContext();
        if (MapUtils.isEmpty(applicationContext.getConfig())
                | !applicationContext.getConfig().containsKey(JwtProtocolConfig.class.getName())) {
            JwtError error = new JwtError(JwtErrorCodes.SERVER_ERROR, null, JWT_ERROR_URI);
            throw new JwtAuthenticationException(error);
        }
        JwtProtocolConfig config = (JwtProtocolConfig) applicationContext.getConfig()
                .get(JwtProtocolConfig.class.getName());
        return new JwtLoginAuthenticationToken(principal, targetUrl, config);
    }
}
