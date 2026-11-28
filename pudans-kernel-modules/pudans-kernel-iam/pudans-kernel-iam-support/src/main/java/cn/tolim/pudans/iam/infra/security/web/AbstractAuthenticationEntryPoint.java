package cn.tolim.pudans.iam.infra.security.web;

import cn.tolim.pudans.iam.infra.util.HttpRequestUtils;
import cn.tolim.pudans.iam.infra.util.IpUtils;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgent;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public abstract class AbstractAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final UserAgentParser userAgentParser;

    protected AbstractAuthenticationEntryPoint(UserAgentParser userAgentParser) {
        this.userAgentParser = userAgentParser;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException,
            ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        UserAgent userAgent = userAgentParser == null ? null
                : userAgentParser.getUserAgent(request);
        logger.warn(
                "身份认证失败：url={}，method={}，ip={}，userAgent={}，headers={}，error={}",
                request.getRequestURL(), request.getMethod(), IpUtils.getIpAddr(request), userAgent,
                HttpRequestUtils.getRequestHeaders(request), authenticationException.getMessage());
    }
}
