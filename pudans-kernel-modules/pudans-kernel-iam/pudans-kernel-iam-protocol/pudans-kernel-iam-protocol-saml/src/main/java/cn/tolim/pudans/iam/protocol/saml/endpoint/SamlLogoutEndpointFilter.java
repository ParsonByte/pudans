package cn.tolim.pudans.iam.protocol.saml.endpoint;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.protocol.saml.application.SamlApplicationService;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.util.SamlRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;


public final class SamlLogoutEndpointFilter extends OncePerRequestFilter {

    private final RequestMatcher requestMatcher;
    private final ApplicationServiceLoader applicationServiceLoader;

    public SamlLogoutEndpointFilter(RequestMatcher requestMatcher,
                                    ApplicationServiceLoader applicationServiceLoader) {
        Assert.notNull(requestMatcher, "requestMatcher must not be null");
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader must not be null");
        this.requestMatcher = requestMatcher;
        this.applicationServiceLoader = applicationServiceLoader;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException,
            IOException {
        if (!requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String appCode = SamlRequestUtils.resolveAppCode(request);
        if (appCode == null) {
            SamlResponseUtils.writeError(response, HttpStatus.NOT_FOUND.value(),
                    "Unknown SAML application");
            return;
        }
        SamlApplicationService applicationService = (SamlApplicationService)
                applicationServiceLoader.getApplicationServiceByAppCode(appCode);
        SamlProtocolConfig config = applicationService.getProtocolConfig(appCode);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        String logoutUrl = config.getSingleLogoutServiceUrl();
        if (StringUtils.isNotBlank(logoutUrl) && isHttpUrl(logoutUrl)) {
            response.sendRedirect(logoutUrl);
            return;
        }
        SamlResponseUtils.writeError(response, HttpStatus.OK.value(), "Logout successful");
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}