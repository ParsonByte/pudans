package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.rbac.service.UiPermissionService;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthenticatedAuthPageWebFilterTest {

    private static final List<String> CONSOLE_PERMISSIONS =
            List.of("dashboard:view", "users:manage", "roles:manage", "audit:view");

    @Mock
    ServerRequestCache serverRequestCache;

    @Mock
    UiPermissionService uiPermissionService;

    private AuthenticatedAuthPageWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthenticatedAuthPageWebFilter(serverRequestCache, uiPermissionService);
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
        return exchange(path, authentication, null);
    }

    private static ServerWebExchange exchange(String path, Authentication authentication, String referer) {
        var builder = MockServerHttpRequest.get(path);
        if (referer != null) {
            builder.header("Referer", referer);
        }
        var delegate = MockServerWebExchange.from(builder.build());
        return new ServerWebExchangeDecorator(delegate) {
            @Override
            public Mono<Principal> getPrincipal() {
                return Mono.justOrEmpty(authentication);
            }
        };
    }

    private static void assertRedirect(ServerWebExchange exchange, String location) {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString(location);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    private void givenUiPermissions(String username, List<String> permissions) {
        when(uiPermissionService.uiPermissionsOf(username)).thenReturn(Mono.just(permissions));
    }

    @Test
    void refererFromConsoleWinsOverSavedRequestAndDefaults() {
        givenUiPermissions("alice", CONSOLE_PERMISSIONS);
        var exchange = exchange("/auth/", user("alice", "super-role"), "http://localhost:8090/console/users?page=2");

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.CONSOLE_LOCATION);
    }

    @Test
    void refererFromUserCenterSendsTheVisitorBackThere() {
        givenUiPermissions("alice", List.of("*"));
        var exchange = exchange("/auth/", user("alice", "super-role"), "http://localhost:8090/uc/security");

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.USER_CENTER_LOCATION);
    }

    @Test
    void consoleRefererIsIgnoredForAccountsWithoutConsolePermissions() {
        when(serverRequestCache.getRedirectUri(any())).thenReturn(Mono.empty());
        givenUiPermissions("bob", List.of("profile:manage"));
        var exchange = exchange("/auth/", user("bob", "normal-role"), "http://localhost:8090/console/users");

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.USER_CENTER_LOCATION);
    }

    @Test
    void savedRequestWinsOverRoleBasedDestination() {
        when(serverRequestCache.getRedirectUri(any()))
                .thenReturn(Mono.just(URI.create("http://localhost/console/users?page=2")));
        givenUiPermissions("alice", CONSOLE_PERMISSIONS);
        var exchange = exchange("/auth/", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, "/console/users?page=2");
    }

    @Test
    void consoleSavedRequestIsSkippedForRegularAccounts() {
        when(serverRequestCache.getRedirectUri(any()))
                .thenReturn(Mono.just(URI.create("http://localhost/console/users")));
        givenUiPermissions("bob", List.of("profile:manage"));
        var exchange = exchange("/auth/", user("bob", "normal-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.USER_CENTER_LOCATION);
    }

    @Test
    void userCenterSavedRequestIsKeptForRegularAccounts() {
        when(serverRequestCache.getRedirectUri(any()))
                .thenReturn(Mono.just(URI.create("http://localhost/uc/security")));
        givenUiPermissions("bob", List.of("profile:manage"));
        var exchange = exchange("/auth/", user("bob", "normal-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, "/uc/security");
    }

    @Test
    void apiSavedRequestsAreIgnored() {
        when(serverRequestCache.getRedirectUri(any()))
                .thenReturn(Mono.just(URI.create("http://localhost/apis/uc.api.pudans.tolim.cn/v1alpha1/users/-")));
        givenUiPermissions("alice", CONSOLE_PERMISSIONS);
        var exchange = exchange("/auth/", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.CONSOLE_LOCATION);
    }

    @Test
    void wildcardUiPermissionsLandInTheConsole() {
        when(serverRequestCache.getRedirectUri(any())).thenReturn(Mono.empty());
        givenUiPermissions("root", List.of("*"));
        var exchange = exchange("/auth/", user("root", "super-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.CONSOLE_LOCATION);
    }

    @Test
    void regularAccountsLandInTheUserCenter() {
        when(serverRequestCache.getRedirectUri(any())).thenReturn(Mono.empty());
        givenUiPermissions("bob", List.of("profile:manage", "authentications:manage"));
        var exchange = exchange("/auth/", user("bob", "normal-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.USER_CENTER_LOCATION);
    }

    @Test
    void indexDocumentPathRedirectsSignedInUsers() {
        when(serverRequestCache.getRedirectUri(any())).thenReturn(Mono.empty());
        givenUiPermissions("alice", CONSOLE_PERMISSIONS);
        var exchange = exchange("/auth/index.html", user("alice", "super-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.CONSOLE_LOCATION);
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
    void recoveryRedirectsSignedInUsers() {
        when(serverRequestCache.getRedirectUri(any())).thenReturn(Mono.empty());
        givenUiPermissions("bob", List.of("profile:manage"));
        var exchange = exchange("/auth/recovery", user("bob", "normal-role"));

        StepVerifier.create(filter.filter(exchange, noRedirectChain())).verifyComplete();

        assertRedirect(exchange, AuthenticatedAuthPageWebFilter.USER_CENTER_LOCATION);
        verify(uiPermissionService).uiPermissionsOf("bob");
    }
}
