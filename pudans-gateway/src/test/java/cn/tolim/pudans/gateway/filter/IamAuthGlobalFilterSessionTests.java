package cn.tolim.pudans.gateway.filter;

import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import cn.tolim.pudans.communal.core.security.AuthenticatedUser;
import cn.tolim.pudans.communal.core.security.JwtTokenService;
import cn.tolim.pudans.gateway.security.ExternalUrlResolver;
import cn.tolim.pudans.gateway.security.GlobalSessionValidator;
import cn.tolim.pudans.gateway.security.SessionValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IamAuthGlobalFilterSessionTests {

    private static final String SECRET = "gateway-session-test-secret-0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "pudans-iam";
    private static final String AUDIENCE = "pudans-api";
    private static final String SESSION_COOKIE = "PUDANS_SID";
    private static final String IAM_USERS_PATH = "/apis/pudans.tolim.cn/v1alpha1/iam/users";
    private static final String APP_ORDERS_PATH = "/apps/order/orders/42";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private IamAuthGlobalFilter filter;
    private AtomicReference<SessionValidationResult> sessionResult;
    private AtomicInteger sessionChecks;
    private AtomicReference<String> lastSid;
    private AtomicReference<String> lastAppCode;
    private String validToken;

    @BeforeEach
    void setUp() {
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, ISSUER, AUDIENCE, 3600);
        validToken = jwtTokenService.issue(new AuthenticatedUser("user-1", "alice", "Alice", "org-1"));
        sessionResult = new AtomicReference<>(SessionValidationResult.active("user-1"));
        sessionChecks = new AtomicInteger();
        lastSid = new AtomicReference<>();
        lastAppCode = new AtomicReference<>();
        GlobalSessionValidator validator = (sid, appCode) -> {
            sessionChecks.incrementAndGet();
            lastSid.set(sid);
            lastAppCode.set(appCode);
            return Mono.just(sessionResult.get());
        };
        filter = new IamAuthGlobalFilter(jwtTokenService, objectMapper, validator,
                new ExternalUrlResolver(true), GatewayAuthOptions.defaults());
    }

    @Test
    void appPageWithoutCredentialsRedirectsToLoginWithAppAndTarget() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(APP_ORDERS_PATH + "?tab=open").build());

        assertThat(continues(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = location(exchange);
        assertThat(location).startsWith("/login?app=order&redirect=");
        assertThat(decodeRedirect(location))
                .isEqualTo("http://localhost" + APP_ORDERS_PATH + "?tab=open");
        assertThat(sessionChecks.get()).isZero();
    }

    @Test
    void appPageWithActiveSessionContinuesAndPassesAppCode() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(APP_ORDERS_PATH).cookie(sessionCookie("sid-1")).build());

        assertThat(continues(exchange)).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(sessionChecks.get()).isEqualTo(1);
        assertThat(lastSid.get()).isEqualTo("sid-1");
        assertThat(lastAppCode.get()).isEqualTo("order");
    }

    @Test
    void forwardedHeadersProduceExternalRedirectTarget() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(APP_ORDERS_PATH)
                        .header(ExternalUrlResolver.FORWARDED_PROTO_HEADER, "https")
                        .header(ExternalUrlResolver.FORWARDED_HOST_HEADER, "home.example.com")
                        .build());

        assertThat(continues(exchange)).isFalse();
        assertThat(decodeRedirect(location(exchange)))
                .isEqualTo("https://home.example.com" + APP_ORDERS_PATH);
    }

    @Test
    void appPageWithInactiveSessionRedirectsAndClearsCookie() {
        sessionResult.set(SessionValidationResult.inactive("revoked"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(APP_ORDERS_PATH).cookie(sessionCookie("sid-1")).build());

        assertThat(continues(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(expiredCookieHeaders(exchange))
                .anyMatch(header -> header.startsWith(SESSION_COOKIE + "=")
                        && header.contains("Max-Age=0")
                        && header.contains("HttpOnly"));
    }

    @Test
    void apiWithoutCredentialsIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IAM_USERS_PATH).build());

        assertUnauthorized(exchange);
        assertThat(sessionChecks.get()).isZero();
    }

    @Test
    void apiWithActiveSessionContinues() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IAM_USERS_PATH).cookie(sessionCookie("sid-1")).build());

        assertThat(continues(exchange)).isTrue();
        assertThat(lastSid.get()).isEqualTo("sid-1");
        assertThat(lastAppCode.get()).isEqualTo("iam");
    }

    @Test
    void apiWithInactiveSessionIsUnauthorizedAndClearsCookie() {
        sessionResult.set(SessionValidationResult.inactive("expired"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IAM_USERS_PATH).cookie(sessionCookie("sid-1")).build());

        assertUnauthorized(exchange);
        assertThat(expiredCookieHeaders(exchange))
                .anyMatch(header -> header.startsWith(SESSION_COOKIE + "=") && header.contains("Max-Age=0"));
    }

    @Test
    void serviceApisRequireSessionAndResolveServiceAppCodes() {
        String repositoryPath = "/apis/pudans.tolim.cn/v1alpha1/repository/files";
        MockServerWebExchange anonymousRepository = MockServerWebExchange.from(
                MockServerHttpRequest.get(repositoryPath).build());
        assertUnauthorized(anonymousRepository);
        assertThat(sessionChecks.get()).isZero();

        MockServerWebExchange repository = MockServerWebExchange.from(
                MockServerHttpRequest.get(repositoryPath).cookie(sessionCookie("sid-1")).build());
        assertThat(continues(repository)).isTrue();
        assertThat(lastAppCode.get()).isEqualTo("repository");

        MockServerWebExchange mq = MockServerWebExchange.from(
                MockServerHttpRequest.get("/apis/pudans.tolim.cn/v1alpha1/mq/messages")
                        .cookie(sessionCookie("sid-1")).build());
        assertThat(continues(mq)).isTrue();
        assertThat(lastAppCode.get()).isEqualTo("mq");

        MockServerWebExchange notification = MockServerWebExchange.from(
                MockServerHttpRequest.get("/apis/pudans.tolim.cn/v1alpha1/notification/send")
                        .cookie(sessionCookie("sid-1")).build());
        assertThat(continues(notification)).isTrue();
        assertThat(lastAppCode.get()).isEqualTo("notification");
    }

    @Test
    void portalLoginConfigAndLogoutUrlArePublic() {
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.get(PublicPathPolicy.PORTAL_LOGIN_CONFIG_PATH).build()))).isTrue();
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.get(PublicPathPolicy.PORTAL_LOGOUT_URL_PATH).build()))).isTrue();
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.get(PublicPathPolicy.LOGIN_CONFIG_PATH).build()))).isTrue();
        assertThat(sessionChecks.get()).isZero();
    }

    @Test
    void portalAndAuthPathsAreValidatedUnlessPublic() {
        String portalMePath = PublicPathPolicy.PORTAL_PATH_PREFIX + "me";
        MockServerWebExchange anonymousPortal = MockServerWebExchange.from(
                MockServerHttpRequest.get(portalMePath).build());
        assertUnauthorized(anonymousPortal);

        MockServerWebExchange portal = MockServerWebExchange.from(
                MockServerHttpRequest.get(portalMePath).cookie(sessionCookie("sid-1")).build());
        assertThat(continues(portal)).isTrue();
        assertThat(lastAppCode.get()).isEqualTo(PublicPathPolicy.IAM_APP_CODE);

        MockServerWebExchange loginGet = MockServerWebExchange.from(
                MockServerHttpRequest.get(PublicPathPolicy.LOGIN_PATH).build());
        assertUnauthorized(loginGet);
    }

    @Test
    void validJwtAuthenticatesWithoutSessionValidation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IAM_USERS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .cookie(sessionCookie("sid-1"))
                        .build());

        assertThat(continues(exchange)).isTrue();
        assertThat(sessionChecks.get()).isZero();
    }

    @Test
    void homepageAndLoginPageArePublic() {
        assertThat(continues(MockServerWebExchange.from(MockServerHttpRequest.get("/").build()))).isTrue();
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.get("/login?app=order&redirect=/apps/order").build()))).isTrue();
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.get("/assets/app.js").build()))).isTrue();
        assertThat(sessionChecks.get()).isZero();
    }

    @Test
    void sessionValidationAndTicketEndpointsArePublic() {
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.post(PublicPathPolicy.SESSION_VALIDATE_PATH).build()))).isTrue();
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.post(PublicPathPolicy.TICKET_ISSUE_PATH).build()))).isTrue();
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.post(PublicPathPolicy.TICKET_EXCHANGE_PATH).build()))).isTrue();
        assertThat(sessionChecks.get()).isZero();
    }

    @Test
    void ssoCallbacksArePublicWithoutSessionValidation() {
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.get("/apps/order/sso/callback?ticket=t-1&target=/orders").build())))
                .isTrue();
        assertThat(continues(MockServerWebExchange.from(
                MockServerHttpRequest.post("/apps/order/sso/backchannel-logout").build()))).isTrue();
        assertThat(sessionChecks.get()).isZero();
    }

    @Test
    void redirectsCanBeDisabledForPageRequests() {
        IamAuthGlobalFilter apiOnlyFilter = new IamAuthGlobalFilter(
                new JwtTokenService(SECRET, ISSUER, AUDIENCE, 3600),
                objectMapper,
                (sid, appCode) -> Mono.just(SessionValidationResult.inactive("revoked")),
                new ExternalUrlResolver(true),
                new GatewayAuthOptions(SESSION_COOKIE, "/login", false, "Lax", false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(APP_ORDERS_PATH).build());

        assertUnauthorized(exchange, apiOnlyFilter);
    }

    @Test
    void unknownPathIsNotFoundEvenWithValidJwt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/other/path")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .build());

        assertNotFound(exchange);
    }

    private static HttpCookie sessionCookie(String sid) {
        return new HttpCookie(SESSION_COOKIE, sid);
    }

    private static String location(MockServerWebExchange exchange) {
        return exchange.getResponse().getHeaders().getFirst(HttpHeaders.LOCATION);
    }

    private static String decodeRedirect(String location) {
        int index = location.indexOf("redirect=");
        assertThat(index).isGreaterThanOrEqualTo(0);
        String encoded = location.substring(index + "redirect=".length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static List<String> expiredCookieHeaders(MockServerWebExchange exchange) {
        List<String> headers = exchange.getResponse().getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(headers).isNotNull();
        return headers;
    }

    private boolean continues(ServerWebExchange exchange) {
        AtomicInteger continued = new AtomicInteger();
        GatewayFilterChain chain = current -> {
            continued.incrementAndGet();
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return continued.get() == 1;
    }

    private void assertUnauthorized(MockServerWebExchange exchange) {
        assertUnauthorized(exchange, filter);
    }

    private void assertUnauthorized(MockServerWebExchange exchange, IamAuthGlobalFilter targetFilter) {
        AtomicInteger continued = new AtomicInteger();
        GatewayFilterChain chain = current -> {
            continued.incrementAndGet();
            return Mono.empty();
        };
        targetFilter.filter(exchange, chain).block();

        assertThat(continued.get()).isZero();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(readBody(exchange));
        assertThat(body.get("code").asString()).isEqualTo(CommonErrorCode.UNAUTHORIZED.code());
        assertThat(body.get("message").asString()).isEqualTo(CommonErrorCode.UNAUTHORIZED.message());
    }

    private void assertNotFound(MockServerWebExchange exchange) {
        AtomicInteger continued = new AtomicInteger();
        GatewayFilterChain chain = current -> {
            continued.incrementAndGet();
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();

        assertThat(continued.get()).isZero();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String readBody(MockServerWebExchange exchange) {
        DataBuffer buffer = DataBufferUtils.join(exchange.getResponse().getBody()).block();
        assertThat(buffer).isNotNull();
        try {
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}