
package cn.tolim.pudans.iam.protocol.jwt.endpoint;

import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.infra.exception.TemplateNotExistException;
import cn.tolim.pudans.iam.protocol.jwt.authentication.JwtAuthenticationFailureHandler;
import cn.tolim.pudans.iam.protocol.jwt.authentication.JwtAuthenticationToken;
import cn.tolim.pudans.iam.protocol.jwt.endpoint.authentication.JwtRequestAuthenticationTokenConverter;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtAuthenticationException;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtErrorCodes;
import cn.tolim.pudans.iam.protocol.jwt.token.IdToken;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.entity.ContentType;
import org.springframework.core.log.LogMessage;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.*;
import static cn.tolim.pudans.iam.protocol.jwt.endpoint.JwtAuthenticationEndpointUtils.throwError;


public final class JwtLoginAuthenticationEndpointFilter extends OncePerRequestFilter {


    private final AuthenticationManager authenticationManager;
    private final RequestMatcher authorizationEndpointMatcher;
    private final Configuration freemarkerTemplateConfiguration = new Configuration(
            Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
    private AuthenticationConverter authenticationConverter;
    private AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource = new WebAuthenticationDetailsSource();
    private AuthenticationSuccessHandler authenticationSuccessHandler = this::sendAuthorizationResponse;
    private AuthenticationFailureHandler authenticationFailureHandler = new JwtAuthenticationFailureHandler();
    private SessionAuthenticationStrategy sessionAuthenticationStrategy = (authentication,
                                                                           request,
                                                                           response) -> {
    };

    public JwtLoginAuthenticationEndpointFilter(RequestMatcher requestMatcher,
                                                AuthenticationManager authenticationManager) {
        Assert.notNull(authenticationManager, "authenticationManager 不能为空");
        Assert.notNull(requestMatcher, "requestMatcher 不能为空");
        this.authenticationManager = authenticationManager;
        this.authorizationEndpointMatcher = requestMatcher;
        this.authenticationConverter = new JwtRequestAuthenticationTokenConverter();
        configFreemarkerTemplate();
    }

    public void setAuthenticationDetailsSource(AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource) {
        Assert.notNull(authenticationDetailsSource, "authenticationDetailsSource 不能为空");
        this.authenticationDetailsSource = authenticationDetailsSource;
    }

    public void setAuthenticationConverter(AuthenticationConverter authenticationConverter) {
        Assert.notNull(authenticationConverter, "authenticationConverter 不能为空");
        this.authenticationConverter = authenticationConverter;
    }

    public void setAuthenticationSuccessHandler(AuthenticationSuccessHandler authenticationSuccessHandler) {
        Assert.notNull(authenticationSuccessHandler, "authenticationSuccessHandler 不能为空");
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    public void setAuthenticationFailureHandler(AuthenticationFailureHandler authenticationFailureHandler) {
        Assert.notNull(authenticationFailureHandler, "authenticationFailureHandler 不能为空");
        this.authenticationFailureHandler = authenticationFailureHandler;
    }

    public void setSessionAuthenticationStrategy(SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        Assert.notNull(sessionAuthenticationStrategy,
                "sessionAuthenticationStrategy 不能为空");
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException,
            IOException {
        if (!this.authorizationEndpointMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Authentication authentication = authenticationConverter.convert(request);

            if (authentication instanceof AbstractAuthenticationToken) {
                ((AbstractAuthenticationToken) authentication)
                        .setDetails(this.authenticationDetailsSource.buildDetails(request));
            }

            Authentication authenticationResult = this.authenticationManager
                    .authenticate(authentication);

            if (!authenticationResult.isAuthenticated()) {
                // If the Principal (Resource Owner) is not authenticated then
                // pass through the chain with the expectation that the authentication process
                // will commence via AuthenticationEntryPoint
                filterChain.doFilter(request, response);
                return;
            }
            this.sessionAuthenticationStrategy.onAuthentication(authenticationResult, request,
                    response);
            this.authenticationSuccessHandler.onAuthenticationSuccess(request, response,
                    authenticationResult);
        } catch (JwtAuthenticationException ex) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace(
                        LogMessage.format("授权请求失败：%s", ex.getError()), ex);
            }
            this.authenticationFailureHandler.onAuthenticationFailure(request, response, ex);
        }
    }

    private void sendAuthorizationResponse(HttpServletRequest request, HttpServletResponse response,
                                           Authentication authentication) {
        JwtAuthenticationToken authenticationToken = (JwtAuthenticationToken) authentication;
        JwtProtocolConfig config = authenticationToken.getConfig();
        IdToken idToken = authenticationToken.getIdToken();
        String targetUri = Objects.toString(authenticationToken.getTargetUrl(), config.getTargetLinkUrl());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(ContentType.TEXT_HTML.getMimeType());
        try {
            Template template = freemarkerTemplateConfiguration.getTemplate("jwt_redirect.ftlh");
            Map<String, Object> data = new HashMap<>(16);
            data.put(NONCE, System.currentTimeMillis());
            data.put(URL, config.getRedirectUrl());
            data.put(BINDING_TYPE, config.getBindingType().getHttpMethod());
            data.put(ID_TOKEN, idToken.getTokenValue());
            data.put(TARGET_URL, targetUri);
            template.process(data, response.getWriter());
        } catch (TemplateException | IOException e) {
            logger.error("模板处理失败", e);
            JwtError error = new JwtError(JwtErrorCodes.SERVER_ERROR,e.getMessage(),JWT_ERROR_URI);
            throwError(error);
        }
    }

    private void configFreemarkerTemplate() {
        try {

            freemarkerTemplateConfiguration
                    .setTemplateLoader(new ClassTemplateLoader(this.getClass(), "/template/"));

            freemarkerTemplateConfiguration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            throw new TemplateNotExistException(exception);
        }
    }
}
