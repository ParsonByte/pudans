package cn.tolim.pudans.iam.protocol.saml.endpoint;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.protocol.saml.application.SamlApplicationService;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.service.SamlProtocolService;
import cn.tolim.pudans.iam.protocol.saml.util.SamlRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public final class SamlMetadataEndpointFilter extends OncePerRequestFilter {

    private final RequestMatcher requestMatcher;
    private final ApplicationServiceLoader applicationServiceLoader;
    private final SamlProtocolService samlProtocolService;

    public SamlMetadataEndpointFilter(RequestMatcher requestMatcher,
                                      ApplicationServiceLoader applicationServiceLoader,
                                      SamlProtocolService samlProtocolService) {
        Assert.notNull(requestMatcher, "requestMatcher must not be null");
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader must not be null");
        Assert.notNull(samlProtocolService, "samlProtocolService must not be null");
        this.requestMatcher = requestMatcher;
        this.applicationServiceLoader = applicationServiceLoader;
        this.samlProtocolService = samlProtocolService;
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
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        SamlApplicationService applicationService = (SamlApplicationService)
                applicationServiceLoader.getApplicationServiceByAppCode(appCode);
        SamlProtocolConfig config = applicationService.getProtocolConfig(appCode);
        String metadata = samlProtocolService.generateMetadata(config);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/samlmetadata+xml;charset=UTF-8");
        response.getWriter().write(metadata);
    }
}