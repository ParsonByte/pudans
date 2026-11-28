package cn.tolim.pudans.iam.protocol.oidc.context;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotConfigException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.oidc.OidcApplicationService;
import cn.tolim.pudans.iam.appkernel.oidc.model.OidcProtocolConfig;
import cn.tolim.pudans.iam.communal.exception.app.AppAccessDeniedException;
import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.infra.security.userdetails.Application;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.infra.util.IpUtils;
import cn.tolim.pudans.iam.infra.util.UrlUtils;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.OidcEndpointConstants.*;
import static cn.tolim.pudans.iam.infra.util.HttpRequestUtils.getRequestHeaders;
import static org.springframework.security.oauth2.server.authorization.settings.ConfigurationSettingNames.Token.*;


public final class OidcAuthorizationServerContextFilter extends OncePerRequestFilter {

    public static final String SEPARATE = "----------------------------------------------------------";

    @Getter
    private final List<EndpointMatcher> endpointMatchers;

    private final ApplicationServiceLoader applicationServiceLoader;

    public OidcAuthorizationServerContextFilter(List<EndpointMatcher> endpointMatchers,
                                                ApplicationServiceLoader applicationServiceLoader) {
        Assert.notNull(endpointMatchers, "requestMatchers 不能为空");
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
        // Validate access permission (skip when not logged in)
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
            OidcApplicationService applicationService = (OidcApplicationService) applicationServiceLoader.getApplicationServiceByAppCode(appCode);
            OidcProtocolConfig config = applicationService.getProtocolConfig(appCode);
            if (Objects.isNull(config)) {
                throw new AppNotExistException();
            }
            if (!config.getConfigured()){
                throw new AppNotConfigException();
            }
            // Wrap ProviderSettings
            StringSubstitutor sub = new StringSubstitutor(variables, "{", "}");
            AuthorizationServerSettings providerSettings = AuthorizationServerSettings.builder()
                    .issuer(sub.replace(UrlUtils.format(ContextService.getPortalPublicBaseUrl() + OIDC_AUTHORIZE_PATH)))
                    .authorizationEndpoint(sub.replace(AUTHORIZATION_ENDPOINT))
                    .tokenEndpoint(sub.replace(TOKEN_ENDPOINT))
                    .jwkSetEndpoint(sub.replace(JWK_SET_ENDPOINT))
                    .oidcClientRegistrationEndpoint(sub.replace(OIDC_CLIENT_REGISTRATION_ENDPOINT))
                    .tokenIntrospectionEndpoint(sub.replace(TOKEN_INTROSPECTION_ENDPOINT))
                    .tokenRevocationEndpoint(sub.replace(TOKEN_REVOCATION_ENDPOINT))
                    .oidcUserInfoEndpoint(sub.replace(OIDC_USER_INFO_ENDPOINT))
                    .oidcLogoutEndpoint(sub.replace(OIDC_LOGOUT_ENDPOINT))
                    .deviceAuthorizationEndpoint(sub.replace(DEVICE_AUTHORIZATION_ENDPOINT))
                    .deviceVerificationEndpoint(sub.replace(DEVICE_VERIFICATION_ENDPOINT))
                    .setting(ACCESS_TOKEN_FORMAT,config.getAccessTokenFormat())
                    .setting(REUSE_REFRESH_TOKENS,config.getReuseRefreshToken())
                    .setting(REFRESH_TOKEN_TIME_TO_LIVE,config.getRefreshTokenTimeToLive())
                    .setting(AUTHORIZATION_CODE_TIME_TO_LIVE,config.getAuthorizationCodeTimeToLive())
                    .setting(DEVICE_CODE_TIME_TO_LIVE,config.getDeviceCodeTimeToLive())
                    .setting(ACCESS_TOKEN_TIME_TO_LIVE,config.getAccessTokenTimeToLive())
                    .setting(ID_TOKEN_SIGNATURE_ALGORITHM,config.getIdTokenSignatureAlgorithm())
                    .build();
            AuthorizationServerContext providerContext = new AuthorizationServerContext() {
                @Override
                public String getIssuer() {
                    return providerSettings.getIssuer();
                }
                @Override
                public AuthorizationServerSettings getAuthorizationServerSettings() {
                    return providerSettings;
                }
            };
            // Set context
            AuthorizationServerContextHolder.setContext(providerContext);
            ApplicationContextHolder.setContext(new DefaultApplicationContext(config));
            filterChain.doFilter(request, response);
        } finally {
            AuthorizationServerContextHolder.resetContext();
            ApplicationContextHolder.resetContext();
        }
    }

    private record DefaultApplicationContext(OidcProtocolConfig config) implements ApplicationContext {

        private DefaultApplicationContext {
            Assert.notNull(config, "config 不能为空");
        }

        // Get application ID
        // Returns String
        @Override
        public String getAppId() {
            return this.config.getAppId();
        }

        // Get client ID
        // Returns String
        @Override
        public String getClientId() {
            return config.getClientId();
        }

        // Get application code
        // Returns String
        @Override
        public String getAppCode() {
            return config.getAppCode();
        }

        // Get application template
        // Returns String
        @Override
        public String getAppTemplate() {
            return config.getAppTemplate();
        }

        // Get protocol configuration
        // Returns Map
        @Override
        public Map<String, Object> getConfig() {
            Map<String, Object> config = new HashMap<>(16);
            config.put(OidcProtocolConfig.class.getName(), this.config);
            return config;
        }

    }
}
