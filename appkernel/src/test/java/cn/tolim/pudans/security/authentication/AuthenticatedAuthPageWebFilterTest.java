package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedAuthPageWebFilterTest {

    private AuthenticatedAuthPageWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthenticatedAuthPageWebFilter();
    }

    private static WebFilterChain noFallThroughChain() {
        return exchange -> Mono.error(new AssertionError("the filter must not fall through"));
    }

    private static WebFilterChain recordingChain(boolean[] called) {
        return exchange -> {
            called[0] = true;
            return Mono.empty();
        };
    }

    private static Authentication user(String name, String... roles) {
        return new UsernamePasswordAuthenticationToken(
                name,
                "n/a",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    private static Authentication anonymous() {
        return new AnonymousAuthenticationToken(
                "anonymous", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_anonymous")));
    }

    private static ServerWebExchange exchange(String path, Authentication authentication) {
        return exchange(MockServerHttpRequest.get(path), authentication);
    }

    private static ServerWebExchange exchange(
            MockServerHttpRequest.BaseBuilder<?> builder, Authentication authentication) {
        var delegate = MockServerWebExchange.from(builder.build());
        return new ServerWebExchangeDecorator(delegate) {
            @Override
            public Mono<Principal> getPrincipal() {
                return Mono.justOrEmpty(authentication);
            }
        };
    }

    private static void assertNoContent(ServerWebExchange exchange) {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(exchange.getResponse().getHeaders().getLocation()).isNull();
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void signInEntryReturnsNoContentForSignedInUsers() {
        var exchange = exchange("/auth/", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noFallThroughChain())).verifyComplete();

        assertNoContent(exchange);
    }

    @Test
    void recoveryReturnsNoContentForSignedInUsers() {
        var exchange = exchange("/auth/recovery", user("bob", "normal-role"));

        StepVerifier.create(filter.filter(exchange, noFallThroughChain())).verifyComplete();

        assertNoContent(exchange);
    }

    @Test
    void indexDocumentPathReturnsNoContentForSignedInUsers() {
        var exchange = exchange("/auth/index.html", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noFallThroughChain())).verifyComplete();

        assertNoContent(exchange);
    }

    @Test
    void loginPageReturnsNoContentForSignedInUsers() {
        var exchange = exchange("/auth/login", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noFallThroughChain())).verifyComplete();

        assertNoContent(exchange);
    }

    @Test
    void anonymousVisitorsPassThrough() {
        var called = new boolean[1];
        var exchange = exchange("/auth/", anonymous());

        StepVerifier.create(filter.filter(exchange, recordingChain(called))).verifyComplete();

        assertThat(called[0]).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void legalPagesStayPublicForSignedInUsers() {
        var called = new boolean[1];
        var exchange = exchange("/auth/legal", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, recordingChain(called))).verifyComplete();

        assertThat(called[0]).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void postLoginPageStaysReachableForSignedInUsers() {
        var called = new boolean[1];
        var exchange = exchange("/auth/done", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, recordingChain(called))).verifyComplete();

        assertThat(called[0]).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
