package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CsrfTokenCookieWebFilterTest {

    private final CsrfTokenCookieWebFilter filter = new CsrfTokenCookieWebFilter();

    private final AtomicBoolean chainCalled = new AtomicBoolean();

    private final WebFilterChain chain = exchange -> {
        chainCalled.set(true);
        return Mono.empty();
    };

    @Test
    void subscribesTokenAttributeSoTheCookieIsWritten() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/sign-in"));
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "token-value");
        AtomicBoolean resolved = new AtomicBoolean();
        exchange.getAttributes().put(CsrfToken.class.getName(), Mono.fromSupplier(() -> {
            resolved.set(true);
            return token;
        }));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(resolved).isTrue();
        assertThat(chainCalled).isTrue();
    }

    @Test
    void continuesChainWhenNoTokenAttributeExists() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, "/logout"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled).isTrue();
    }
}
