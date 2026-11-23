package cn.tolim.pudans.core.user.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserEndpointLoginNameUpdateTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    Accounts accounts;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private UserEndpoint endpoint;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        endpoint = new UserEndpoint(
                client, accounts, null, null, null, null, null, null, domainEventPublisher, null, null, null, null);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void updatesTheLoginNameWithoutChangingTheResourceName() {
        var user = user("alice");
        when(accounts.getUser("alice")).thenReturn(Mono.just(user));
        when(accounts.isLoginNameAvailable("alice.login", "alice")).thenReturn(Mono.just(true));
        when(client.update(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient
                .put()
                .uri("/users/alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("loginName", "Alice.Login"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.metadata.name")
                .isEqualTo("alice")
                .jsonPath("$.spec.loginName")
                .isEqualTo("alice.login");

        var updated = ArgumentCaptor.forClass(User.class);
        verify(client).update(updated.capture());
        assertThat(updated.getValue().getMetadata().getName()).isEqualTo("alice");
        assertThat(updated.getValue().getSpec().getLoginName()).isEqualTo("alice.login");
    }

    @Test
    void resetsACustomLoginNameWhenTheFieldIsBlank() {
        var user = user("alice");
        user.getSpec().setLoginName("old.login");
        when(accounts.getUser("alice")).thenReturn(Mono.just(user));
        when(accounts.isLoginNameAvailable("alice", "alice")).thenReturn(Mono.just(true));
        when(client.update(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webTestClient
                .put()
                .uri("/users/alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("loginName", ""))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.metadata.name")
                .isEqualTo("alice")
                .jsonPath("$.spec.loginName")
                .isEqualTo("alice");
    }

    @Test
    void rejectsALoginNameThatIsAlreadyTaken() {
        when(accounts.getUser("alice")).thenReturn(Mono.just(user("alice")));
        when(accounts.isLoginNameAvailable("bob", "alice")).thenReturn(Mono.just(false));

        webTestClient
                .put()
                .uri("/users/alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("loginName", "bob"))
                .exchange()
                .expectStatus()
                .isBadRequest();

        verify(client, never()).update(any(User.class));
    }

    @Test
    void rejectsAnInvalidLoginName() {
        when(accounts.getUser("alice")).thenReturn(Mono.just(user("alice")));

        webTestClient
                .put()
                .uri("/users/alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("loginName", "bad_name"))
                .exchange()
                .expectStatus()
                .isBadRequest();

        verify(accounts, never()).isLoginNameAvailable(any(), any());
        verify(client, never()).update(any(User.class));
    }

    @Test
    void preventsUsersFromChangingTheirOwnLoginName() {
        when(accounts.getUser("alice")).thenReturn(Mono.just(user("alice")));
        var selfClient = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter(authenticatedAs("alice"))
                .build();

        selfClient
                .put()
                .uri("/users/-")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("loginName", "alice.login"))
                .exchange()
                .expectStatus()
                .isBadRequest();

        verify(client, never()).update(any(User.class));
    }

    private static WebFilter authenticatedAs(String username) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(username, "n/a", List.of());
        return (exchange, chain) -> chain.filter(exchange)
                .contextWrite(context ->
                        context.put(SecurityContext.class, Mono.just(new SecurityContextImpl(authentication))));
    }

    private static User user(String name) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setDisplayName("Alice");
        user.setSpec(spec);
        return user;
    }
}
