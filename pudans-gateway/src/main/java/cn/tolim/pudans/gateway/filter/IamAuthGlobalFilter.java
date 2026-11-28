package cn.tolim.pudans.gateway.filter;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import cn.tolim.pudans.communal.core.security.JwtTokenService;
import cn.tolim.pudans.communal.core.security.JwtValidationException;
import cn.tolim.pudans.gateway.config.GatewayProperties;
import cn.tolim.pudans.gateway.security.ExternalUrlResolver;
import cn.tolim.pudans.gateway.security.GlobalSessionValidator;
import cn.tolim.pudans.gateway.security.SessionValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Component
public class IamAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String API_BASE_PATH = PublicPathPolicy.API_BASE_PATH;
    public static final String API_PATH_PREFIX = PublicPathPolicy.API_PATH_PREFIX;
    public static final String LOGIN_PATH = PublicPathPolicy.LOGIN_PATH;
    public static final String ACTUATOR_PATH = PublicPathPolicy.ACTUATOR_PATH;
    public static final String HEALTH_PATH = PublicPathPolicy.HEALTH_PATH;
    public static final String INFO_PATH = PublicPathPolicy.INFO_PATH;
    public static final String APP_QUERY_PARAM = "app";
    public static final String REDIRECT_QUERY_PARAM = "redirect";
    public static final String SESSION_COOKIE_PATH = "/";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;
    private final GlobalSessionValidator globalSessionValidator;
    private final ExternalUrlResolver externalUrlResolver;
    private final GatewayAuthOptions authOptions;

    public IamAuthGlobalFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper) {
        this(jwtTokenService, objectMapper, GlobalSessionValidator.disabled(),
                new ExternalUrlResolver(true), GatewayAuthOptions.defaults());
    }

    public IamAuthGlobalFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper,
                               GlobalSessionValidator globalSessionValidator,
                               ExternalUrlResolver externalUrlResolver,
                               GatewayAuthOptions authOptions) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
        this.globalSessionValidator = globalSessionValidator;
        this.externalUrlResolver = externalUrlResolver;
        this.authOptions = authOptions;
    }

    @Autowired
    public IamAuthGlobalFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper,
                               GlobalSessionValidator globalSessionValidator,
                               GatewayProperties gatewayProperties) {
        this(jwtTokenService, objectMapper, globalSessionValidator,
                new ExternalUrlResolver(gatewayProperties.getAuth().isTrustForwardedHeaders()),
                new GatewayAuthOptions(
                        gatewayProperties.getAuth().getSessionCookieName(),
                        gatewayProperties.getAuth().getLoginPageUrl(),
                        gatewayProperties.getAuth().isCookieSecure(),
                        gatewayProperties.getAuth().getCookieSameSite(),
                        gatewayProperties.getAuth().getCookieDomain(),
                        gatewayProperties.getAuth().isRedirectPageRequests()));
    }

    static boolean isPublicRequest(HttpMethod method, String path) {
        return PublicPathPolicy.isPublic(method, path);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        if (!PublicPathPolicy.isRoutedPath(path)) {
            return notFound(exchange.getResponse());
        }
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }
        if (PublicPathPolicy.isPublic(method, path)) {
            return chain.filter(exchange);
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            return authenticateWithJwt(exchange, chain, authorization, method, path);
        }
        String sid = readSessionId(request);
        if (sid == null) {
            return reject(exchange, method, path, false);
        }
        String appCode = PublicPathPolicy.resolveAppCode(path);
        return globalSessionValidator.validate(sid, appCode)
                .defaultIfEmpty(SessionValidationResult.inactive("empty-session-validation"))
                .flatMap(result -> result.active()
                        ? chain.filter(exchange)
                        : reject(exchange, method, path, true));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> authenticateWithJwt(ServerWebExchange exchange, GatewayFilterChain chain,
                                           String authorization, HttpMethod method, String path) {
        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return reject(exchange, method, path, false);
        }
        try {
            jwtTokenService.parse(authorization.substring(BEARER_PREFIX.length()).trim());
        } catch (JwtValidationException exception) {
            return reject(exchange, method, path, false);
        }
        return chain.filter(exchange);
    }

    private String readSessionId(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(authOptions.sessionCookieName());
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        return cookie.getValue();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpMethod method, String path, boolean expiredSession) {
        ServerHttpResponse response = exchange.getResponse();
        if (expiredSession) {
            response.getHeaders().add(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString());
        }
        if (authOptions.redirectPageRequests() && isPageRequest(method, path)) {
            return redirectToLogin(exchange, path);
        }
        return unauthorized(response);
    }

    private static boolean isPageRequest(HttpMethod method, String path) {
        if (!PublicPathPolicy.isPageRoute(path)) {
            return false;
        }
        return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
    }

    private Mono<Void> redirectToLogin(ServerWebExchange exchange, String path) {
        String appCode = PublicPathPolicy.resolveAppCode(path);
        String target = externalUrlResolver.resolve(exchange.getRequest());
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authOptions.loginPageUrl());
        if (appCode != null && !appCode.isBlank()) {
            builder.queryParam(APP_QUERY_PARAM, appCode);
        }
        builder.queryParam(REDIRECT_QUERY_PARAM, target);
        String location = builder.build().encode().toUriString();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, location);
        response.getHeaders().setCacheControl("no-store");
        return response.setComplete();
    }

    private ResponseCookie expiredSessionCookie() {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(authOptions.sessionCookieName(), "")
                .path(SESSION_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .httpOnly(true)
                .secure(authOptions.cookieSecure());
        if (authOptions.cookieSameSite() != null && !authOptions.cookieSameSite().isBlank()) {
            builder.sameSite(authOptions.cookieSameSite());
        }
        if (authOptions.cookieDomain() != null && !authOptions.cookieDomain().isBlank()) {
            builder.domain(authOptions.cookieDomain().trim());
        }
        return builder.build();
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        return writeError(response, HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED);
    }

    private Mono<Void> notFound(ServerHttpResponse response) {
        return writeError(response, HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND);
    }

    private Mono<Void> writeError(ServerHttpResponse response, HttpStatus status,
                                  CommonErrorCode errorCode) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = objectMapper.writeValueAsBytes(
                ApiResponse.error(errorCode.code(), errorCode.message()));
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}