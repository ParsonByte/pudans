package cn.tolim.pudans.gateway.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IamSessionValidationClientTests {

    private static final String VALIDATE_PATH = "/apis/pudans.tolim.cn/v1alpha1/iam/session/validate";
    private static final String WRAPPED_ACTIVE_RESPONSE =
            "{\"code\":\"OK\",\"message\":\"OK\",\"data\":{\"active\":true,\"sub\":\"user-1\"}}";
    private static final String WRAPPED_INACTIVE_RESPONSE =
            "{\"code\":\"OK\",\"message\":\"OK\",\"data\":{\"active\":false,\"reason\":\"revoked\"}}";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private HttpServer server;
    private AtomicInteger requestCount;
    private AtomicInteger responseStatus;
    private AtomicReference<String> responseBody;

    @BeforeEach
    void startServer() throws IOException {
        requestCount = new AtomicInteger();
        responseStatus = new AtomicInteger(200);
        responseBody = new AtomicReference<>(WRAPPED_ACTIVE_RESPONSE);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(VALIDATE_PATH, exchange -> {
            requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void activeWrappedResponseIsParsedAndCached() {
        IamSessionValidationClient client = client(10);

        SessionValidationResult first = client.validate("sid-1", "order").block();
        SessionValidationResult second = client.validate("sid-1", "order").block();

        assertThat(first).isNotNull();
        assertThat(first.active()).isTrue();
        assertThat(first.subject()).isEqualTo("user-1");
        assertThat(second).isEqualTo(first);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void differentAppCodesAreCachedSeparately() {
        IamSessionValidationClient client = client(10);

        client.validate("sid-1", "order").block();
        client.validate("sid-1", "billing").block();

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void inactiveResponseCarriesReason() {
        responseBody.set(WRAPPED_INACTIVE_RESPONSE);
        IamSessionValidationClient client = client(10);

        SessionValidationResult result = client.validate("sid-1", "order").block();

        assertThat(result).isNotNull();
        assertThat(result.active()).isFalse();
        assertThat(result.reason()).isEqualTo("revoked");
    }

    @Test
    void unwrappedResponseIsSupported() {
        responseBody.set("{\"active\":true,\"sub\":\"user-2\"}");
        IamSessionValidationClient client = client(10);

        SessionValidationResult result = client.validate("sid-2", null).block();

        assertThat(result).isNotNull();
        assertThat(result.active()).isTrue();
        assertThat(result.subject()).isEqualTo("user-2");
    }

    @Test
    void iamErrorFailsClosedAndIsNotCached() {
        responseStatus.set(500);
        IamSessionValidationClient client = client(10);

        SessionValidationResult first = client.validate("sid-1", "order").block();
        SessionValidationResult second = client.validate("sid-1", "order").block();

        assertThat(first).isNotNull();
        assertThat(first.active()).isFalse();
        assertThat(first.reason()).isEqualTo("session-validation-unavailable");
        assertThat(second).isNotNull();
        assertThat(second.active()).isFalse();
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void blankSidIsRejectedWithoutCallingIam() {
        IamSessionValidationClient client = client(10);

        SessionValidationResult result = client.validate(" ", "order").block();

        assertThat(result).isNotNull();
        assertThat(result.active()).isFalse();
        assertThat(result.reason()).isEqualTo("missing-sid");
        assertThat(requestCount.get()).isZero();
    }

    @Test
    void zeroCacheTtlDisablesReuse() {
        IamSessionValidationClient client = client(0);

        client.validate("sid-1", "order").block();
        client.validate("sid-1", "order").block();

        assertThat(requestCount.get()).isEqualTo(2);
    }

    private IamSessionValidationClient client(long cacheTtlSeconds) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + VALIDATE_PATH;
        return new IamSessionValidationClient(WebClient.builder(), objectMapper, url, cacheTtlSeconds, 3000);
    }
}