package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.properties.SecurityProperties;
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
        filter = new AuthenticatedAuthPageWebFilter(new SecurityProperties());
    }

    private static WebFilterChain noRedirectChain() {
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
        var delegate = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return new ServerWebExchangeDecorator(delegate) {
            @Override
            public Mono<Principal> getPrincipal() {
                return Mono.justOrEmpty(authentication);
            }
        };
    }

    private static void assertRedirect(ServerWebExchange exchange) {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString("/auth/done");
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void signInEntryRedirectsAuthenticatedUsersToThePostLoginPage() {
        var exchange = exchange("/auth/", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange);
    }

    @Test
    void recoveryRedirectsAuthenticatedUsers() {
        var exchange = exchange("/auth/recovery", user("bob", "normal-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange);
    }

    @Test
    void indexDocumentPathRedirectsSignedInUsers() {
        var exchange = exchange("/auth/index.html", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange);
    }

    @Test
    void anonymousVisitorsKeepTheSignInPage() {
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
