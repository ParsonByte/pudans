package cn.tolim.pudans.iam.protocol.core;

import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.infra.security.savedredirect.HttpSessionRedirectCache;
import cn.tolim.pudans.iam.infra.security.savedredirect.RedirectCache;
import cn.tolim.pudans.iam.infra.security.web.AbstractAuthenticationEntryPoint;
import cn.tolim.pudans.iam.infra.util.HttpResponseUtils;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.util.Objects;

import static cn.tolim.pudans.iam.infra.context.ServletContextService.isHtmlRequest;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;


public class UnauthorizedAuthenticationEntryPoint extends AbstractAuthenticationEntryPoint {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final RedirectCache redirectCache = new HttpSessionRedirectCache();

    public UnauthorizedAuthenticationEntryPoint(UserAgentParser userAgentParser) {
        super(userAgentParser);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // Record
        redirectCache.saveRedirect(request, response, RedirectCache.RedirectType.REQUEST);
        // Check request
        boolean isHtmlRequest = isHtmlRequest(request);
        // HTML
        if (isHtmlRequest) {
            // Redirect to the frontend session-expired route
            response.sendRedirect(ContextService.getPortalLoginUrl());
            return;
        }
        // JSON
        ApiRestResult<Object> result = ApiRestResult.builder()
                .status(String.valueOf(UNAUTHORIZED.value()))
                .message(Objects.toString(authException.getMessage(), UNAUTHORIZED.getReasonPhrase()))
                .build();
        HttpResponseUtils.flushResponseJson(response, UNAUTHORIZED.value(), result);
    }
}
