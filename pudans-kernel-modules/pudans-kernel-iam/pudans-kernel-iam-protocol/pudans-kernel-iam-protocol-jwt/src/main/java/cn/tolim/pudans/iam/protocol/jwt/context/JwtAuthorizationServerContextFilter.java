
package cn.tolim.pudans.iam.protocol.jwt.context;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotConfigException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.jwt.JwtApplicationService;
import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.communal.exception.app.AppAccessDeniedException;
import cn.tolim.pudans.iam.infra.security.userdetails.Application;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.infra.util.IpUtils;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE;
import static cn.tolim.pudans.iam.infra.util.HttpRequestUtils.getRequestHeaders;


public final class JwtAuthorizationServerContextFilter extends OncePerRequestFilter {

    public static final String SEPARATE = "----------------------------------------------------------";

    @Getter
    private final List<EndpointMatcher> endpointMatchers;

    private final ApplicationServiceLoader applicationServiceLoader;

    public JwtAuthorizationServerContextFilter(List<EndpointMatcher> endpointMatchers,
                                               ApplicationServiceLoader applicationServiceLoader) {
        Assert.notNull(endpointMatchers, "endpointMatchers 不能为空");
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader 不能为空");
        this.applicationServiceLoader = applicationServiceLoader;
        this.endpointMatchers = endpointMatchers;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException,
            IOException {

        boolean match = false, access = false;
        Map<String, String> variables = new HashMap<>(16);
        for (EndpointMatcher endpointMatcher : endpointMatchers) {
            RequestMatcher requestMatcher = endpointMatcher.getRequestMatcher();
            if (requestMatcher.matches(request)) {
                match = true;
                access = endpointMatcher.getAccess();
                variables = requestMatcher.matcher(request).getVariables();
            }
        }
        if (!match) {
            filterChain.doFilter(request, response);
            return;
        }
        String appCode = variables.get(APP_CODE);

        if (access && SecurityUtils.isAuthenticated()) {
            UserDetails userDetails = SecurityUtils.getCurrentUser();
            Collection<Application> applications = userDetails.getApplications();
            if (applications.stream()
                    .noneMatch(application -> application.getCode().equals(appCode))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        new AppAccessDeniedException().getMessage());
                return;
            }
        }
        try {
            if (this.logger.isTraceEnabled()) {
                String body = IOUtils.toString(request.getInputStream(),StandardCharsets.UTF_8).replaceAll("\\s+", " ");
                String logs = "\n" +
                        "┣ " + SEPARATE + "\n" +
                        "┣ App: " + appCode + "\n" +
                        "┣ Request url: " + request.getMethod() + " " + request.getRequestURL() + "\n" +
                        "┣ Request ip: " + IpUtils.getIpAddr(request) + "\n" +
                        "┣ Request headers: " + JSONObject.toJSONString(getRequestHeaders(request)) + "\n" +
                        "┣ Request parameters: " + JSONObject.toJSONString(request.getParameterMap()) + "\n" +
                        "┣ Request payload: " + StringUtils.defaultIfBlank(body, "-") + "\n" +
                        "┣ " + SEPARATE;
                logger.trace(logs);
            }
            
            JwtApplicationService applicationService = (JwtApplicationService) applicationServiceLoader.getApplicationServiceByAppCode(appCode);
            JwtProtocolConfig config = applicationService.getProtocolConfig(appCode);
            if (Objects.isNull(config)) {
                throw new AppNotExistException();
            }
            if (!config.getConfigured()){
                throw new AppNotConfigException();
            }
            
            ApplicationContextHolder.setContext(new DefaultApplicationContext(config));
            filterChain.doFilter(request, response);
        } finally {
            ApplicationContextHolder.resetContext();
        }
    }

    private record DefaultApplicationContext(JwtProtocolConfig config) implements ApplicationContext {

        private DefaultApplicationContext {
            Assert.notNull(config, "config 不能为空");
        }


        @Override
        public String getAppId() {
            return this.config.getAppId();
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
            Map<String, Object> config = new HashMap<>(16);
            config.put(JwtProtocolConfig.class.getName(), this.config);
            return config;
        }

    }
}
