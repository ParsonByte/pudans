package cn.tolim.pudans.gateway.filter;

import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import cn.tolim.pudans.communal.core.security.AuthenticatedUser;
import cn.tolim.pudans.communal.core.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class IamAuthGlobalFilterTests {

    private static final String SECRET = "gateway-test-secret-0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "pudans-iam";
    private static final String AUDIENCE = "pudans-api";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private IamAuthGlobalFilter filter;
    private String validToken;

    @BeforeEach
    void setUp() {
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, ISSUER, AUDIENCE, 3600);
        filter = new IamAuthGlobalFilter(jwtTokenService, objectMapper);
        validToken = jwtTokenService.issue(new AuthenticatedUser("user-1", "alice", "Alice", "org-1"));
    }

    @Test
    void optionsRequestWithinApiPrefixIsAllowed() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options(IamAuthGlobalFilter.API_BASE_PATH + "/iam/users").build());

        assertThat(continues(exchange)).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void optionsRequestOutsideApiPrefixIsNotFound() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/other/path").build());

        assertNotFound(exchange);
    }

    @Test
    void loginPostIsPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(IamAuthGlobalFilter.LOGIN_PATH).build());

        assertThat(continues(exchange)).isTrue();
    }

    @Test
    void loginPostWithQueryStringIsPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(IamAuthGlobalFilter.LOGIN_PATH + "?tenant=acme").build());

        assertThat(continues(exchange)).isTrue();
    }

    @Test
    void loginGetRequiresToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.LOGIN_PATH).build());

        assertUnauthorized(exchange);
    }

    @Test
    void loginPostWithTrailingSlashRequiresToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(IamAuthGlobalFilter.LOGIN_PATH + "/").build());

        assertUnauthorized(exchange);
    }

    @Test
    void actuatorHealthIsPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.HEALTH_PATH).build());

        assertThat(continues(exchange)).isTrue();
    }

    @Test
    void actuatorInfoIsPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.INFO_PATH).build());

        assertThat(continues(exchange)).isTrue();
    }

    @Test
    void pathOutsideApiBaseIsNotFound() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/other/path")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .build());

        assertNotFound(exchange);
    }

    @Test
    void siblingPrefixPathIsNotFound() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.API_BASE_PATH + "xxx/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .build());

        assertNotFound(exchange);
    }

    @Test
    void legacyActuatorHealthPathIsNotFound() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());

        assertNotFound(exchange);
    }

    @Test
    void protectedPathWithoutTokenIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.API_BASE_PATH + "/iam/users").build());

        assertUnauthorized(exchange);
    }

    @Test
    void protectedPathWithBlankBearerTokenIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.API_BASE_PATH + "/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                        .build());

        assertUnauthorized(exchange);
    }

    @Test
    void protectedPathWithMalformedTokenIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.API_BASE_PATH + "/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .build());

        assertUnauthorized(exchange);
    }

    @Test
    void protectedPathWithTokenForDifferentAudienceIsUnauthorized() {
        JwtTokenService foreignTokenService = new JwtTokenService(SECRET, ISSUER, "other-api", 3600);
        String foreignToken = foreignTokenService.issue(new AuthenticatedUser("user-1", null, null, null));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.API_BASE_PATH + "/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken)
                        .build());

        assertUnauthorized(exchange);
    }

    @Test
    void protectedPathWithValidTokenContinues() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.API_BASE_PATH + "/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .build());

        assertThat(continues(exchange)).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void bearerSchemeIsCaseInsensitive() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(IamAuthGlobalFilter.API_BASE_PATH + "/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, "bearer " + validToken)
                        .build());

        assertThat(continues(exchange)).isTrue();
    }

    @Test
    void orderIsHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    private boolean continues(ServerWebExchange exchange) {
        AtomicBoolean continued = new AtomicBoolean(false);
        GatewayFilterChain chain = current -> {
            continued.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return continued.get();
    }

    private void assertUnauthorized(MockServerWebExchange exchange) {
        assertErrorResponse(exchange, HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED);
    }

    private void assertNotFound(MockServerWebExchange exchange) {
        assertErrorResponse(exchange, HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND);
    }

    private void assertErrorResponse(MockServerWebExchange exchange, HttpStatus status,
                                     CommonErrorCode errorCode) {
        AtomicBoolean continued = new AtomicBoolean(false);
        GatewayFilterChain chain = current -> {
            continued.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();

        assertThat(continued.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode body = objectMapper.readTree(readBody(exchange));
        assertThat(body.get("code").asString()).isEqualTo(errorCode.code());
        assertThat(body.get("message").asString()).isEqualTo(errorCode.message());
        assertThat(body.get("data").isNull()).isTrue();
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
