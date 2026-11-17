package cn.tolim.pudans.infra.webfilter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SpaIndexWebFilterTest {

    private final SpaIndexWebFilter filter = new SpaIndexWebFilter();

    @Test
    void rewritesClientRouteToIndex() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/sign-in")
                .header("Accept", MediaType.TEXT_HTML_VALUE)
                .build());
        var path = new AtomicReference<String>();

        StepVerifier.create(filter.filter(exchange, chainThatCaptures(path))).verifyComplete();

        assertThat(path).hasValue("/index.html");
    }

    @Test
    void preservesQueryWhenRewriting() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/legal?tab=privacy")
                .header("Accept", MediaType.TEXT_HTML_VALUE)
                .build());
        var path = new AtomicReference<String>();
        var query = new AtomicReference<String>();

        WebFilterChain chain = rewritten -> {
            path.set(rewritten.getRequest().getPath().value());
            query.set(rewritten.getRequest().getURI().getQuery());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(path).hasValue("/index.html");
        assertThat(query).hasValue("tab=privacy");
    }

    @Test
    void leavesApiRequestsUntouched() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                        "/apis/api.pudans.tolim.cn/v1alpha1/login/status")
                .header("Accept", MediaType.TEXT_HTML_VALUE)
                .build());
        var path = new AtomicReference<String>();

        StepVerifier.create(filter.filter(exchange, chainThatCaptures(path))).verifyComplete();

        assertThat(path).hasValue("/apis/api.pudans.tolim.cn/v1alpha1/login/status");
    }

    @Test
    void leavesStaticAssetsUntouched() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/assets/index-abc.js")
                .header("Accept", MediaType.TEXT_HTML_VALUE)
                .build());
        var path = new AtomicReference<String>();

        StepVerifier.create(filter.filter(exchange, chainThatCaptures(path))).verifyComplete();

        assertThat(path).hasValue("/assets/index-abc.js");
    }

    @Test
    void leavesNonHtmlRequestsUntouched() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/sign-in")
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build());
        var path = new AtomicReference<String>();

        StepVerifier.create(filter.filter(exchange, chainThatCaptures(path))).verifyComplete();

        assertThat(path).hasValue("/sign-in");
    }

    @Test
    void leavesNonGetRequestsUntouched() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, "/sign-in")
                .header("Accept", MediaType.TEXT_HTML_VALUE)
                .build());
        var path = new AtomicReference<String>();

        StepVerifier.create(filter.filter(exchange, chainThatCaptures(path))).verifyComplete();

        assertThat(path).hasValue("/sign-in");
    }

    private static WebFilterChain chainThatCaptures(AtomicReference<String> path) {
        return exchange -> {
            path.set(exchange.getRequest().getPath().value());
            return Mono.empty();
        };
    }
}
