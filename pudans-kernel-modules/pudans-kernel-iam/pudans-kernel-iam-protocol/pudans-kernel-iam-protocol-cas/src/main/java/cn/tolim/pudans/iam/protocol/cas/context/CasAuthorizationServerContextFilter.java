package cn.tolim.pudans.iam.protocol.cas.context;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotConfigException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.communal.exception.app.AppAccessDeniedException;
import cn.tolim.pudans.iam.infra.security.userdetails.Application;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.protocol.cas.application.CasApplicationService;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;
import cn.tolim.pudans.iam.protocol.cas.util.CasRequestUtils;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public final class CasAuthorizationServerContextFilter extends OncePerRequestFilter {

    @Getter
    private final List<EndpointMatcher> endpointMatchers;

    private final ApplicationServiceLoader applicationServiceLoader;

    public CasAuthorizationServerContextFilter(List<EndpointMatcher> endpointMatchers,
                                               ApplicationServiceLoader applicationServiceLoader) {
        Assert.notNull(endpointMatchers, "endpointMatchers must not be null");
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader must not be null");
        this.endpointMatchers = endpointMatchers;
        this.applicationServiceLoader = applicationServiceLoader;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException,
            IOException {
        boolean matched = false;
        boolean access = false;
        for (EndpointMatcher endpointMatcher : endpointMatchers) {
            RequestMatcher requestMatcher = endpointMatcher.getRequestMatcher();
            if (requestMatcher.matches(request)) {
                matched = true;
                access = Boolean.TRUE.equals(endpointMatcher.getAccess());
            }
        }
        if (!matched) {
            filterChain.doFilter(request, response);
            return;
        }
        String appCode = CasRequestUtils.resolveAppCode(request);
        if (appCode == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (access && SecurityUtils.isAuthenticated()) {
            UserDetails userDetails = SecurityUtils.getCurrentUser();
            Collection<Application> applications = userDetails == null
                    ? List.of() : userDetails.getApplications();
            if (applications.stream()
                    .noneMatch(application -> Objects.equals(application.getCode(), appCode))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        new AppAccessDeniedException().getMessage());
                return;
            }
        }
        try {
            CasApplicationService applicationService = (CasApplicationService)
                    applicationServiceLoader.getApplicationServiceByAppCode(appCode);
            CasProtocolConfig config = applicationService.getProtocolConfig(appCode);
            if (config == null) {
                throw new AppNotExistException();
            }
            if (!config.getConfigured()) {
                throw new AppNotConfigException();
            }
            ApplicationContextHolder.setContext(new DefaultApplicationContext(config));
            filterChain.doFilter(request, response);
        } finally {
            ApplicationContextHolder.resetContext();
        }
    }

    private record DefaultApplicationContext(CasProtocolConfig config)
            implements ApplicationContext {

        private DefaultApplicationContext {
            Assert.notNull(config, "config must not be null");
        }

        @Override
        public String getAppId() {
            return config.getAppId();
        }

        @Override
        public String getClientId() {
            return config.getClientId();
        }

        @Override
        public String getAppCode() {
            return config.getAppCode();
        }

        @Override
        public String getAppTemplate() {
            return config.getAppTemplate();
        }

        @Override
        public Map<String, Object> getConfig() {
            Map<String, Object> values = new HashMap<>(16);
            values.put(CasProtocolConfig.class.getName(), config);
            return values;
        }
    }
}