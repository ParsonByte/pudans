package cn.tolim.pudans.core.user.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.Metadata;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class UserEndpointPasswordChangeTest {

    @Mock
    UserService userService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var endpoint = new UserEndpoint(
                null, userService, null, null, null, null, null, null, null, null, null, null);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter(authenticatedAs("admin"))
                .build();
    }

    @Test
    void selfServicePasswordChangeUsesTheAuthenticatedUser() {
        when(userService.confirmPassword("admin", "old-password")).thenReturn(Mono.just(true));
        when(userService.updateWithRawPassword("admin", "new-password")).thenReturn(Mono.just(user("admin")));

        webTestClient
                .put()
                .uri("/users/-/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("password", "new-password", "oldPassword", "old-password"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.metadata.name")
                .isEqualTo("admin");
    }

    @Test
    void selfServicePasswordChangeRejectsAWrongOldPassword() {
        when(userService.confirmPassword("admin", "wrong-password")).thenReturn(Mono.just(false));

        webTestClient
                .put()
                .uri("/users/-/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("password", "new-password", "oldPassword", "wrong-password"))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void selfServicePasswordChangeWithoutLoginIsNotAnInternalError() {
        var endpoint = new UserEndpoint(
                null, userService, null, null, null, null, null, null, null, null, null, null);
        var anonymousClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        anonymousClient
                .put()
                .uri("/users/-/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("password", "new-password", "oldPassword", "old-password"))
                .exchange()
                .expectStatus()
                .isBadRequest();
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
        user.setSpec(new User.UserSpec());
        return user;
    }
}
