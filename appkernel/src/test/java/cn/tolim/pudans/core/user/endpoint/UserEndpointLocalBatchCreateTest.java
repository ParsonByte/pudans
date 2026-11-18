package cn.tolim.pudans.core.user.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.user.service.LocalUserService;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserEndpointLocalBatchCreateTest {

    @Mock
    LocalUserService localUserService;

    @Mock
    AuditService auditService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var endpoint = new UserEndpoint(
                null, null, null, null, null, auditService, null, null, null, null, localUserService, null);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void reportsCreatedAndFailedUsersPerItem() {
        when(localUserService.create(
                        eq("alice"), isNull(), isNull(), eq("secret123"), isNull(), isNull()))
                .thenReturn(Mono.just(user("alice")));
        when(localUserService.create(
                        eq("bob"), isNull(), isNull(), eq("secret123"), isNull(), isNull()))
                .thenReturn(Mono.error(new DuplicateNameException("用户名 bob 已存在，请更换用户名后重试。")));
        when(auditService.recordChild(
                        any(),
                        isNull(),
                        eq("BATCH_CREATE_USER"),
                        eq("User"),
                        eq("alice"),
                        eq("SUCCESS"),
                        anyMap(),
                        isNull()))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/users/-/local/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "users",
                        List.of(
                                Map.of("username", "alice", "password", "secret123"),
                                Map.of("username", "bob", "password", "secret123"))))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.created")
                .isEqualTo(1)
                .jsonPath("$.failed")
                .isEqualTo(1)
                .jsonPath("$.failures[0].index")
                .isEqualTo(2)
                .jsonPath("$.failures[0].username")
                .isEqualTo("bob")
                .jsonPath("$.failures[0].message")
                .isEqualTo("用户名 bob 已存在，请更换用户名后重试。");
    }

    @Test
    void reportsInvalidItemsWithoutCreatingThem() {
        webTestClient
                .post()
                .uri("/users/-/local/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "users",
                        List.of(Map.of("username", "Bad Name", "password", "secret123"))))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.created")
                .isEqualTo(0)
                .jsonPath("$.failed")
                .isEqualTo(1)
                .jsonPath("$.failures[0].index")
                .isEqualTo(1);

        verifyNoInteractions(localUserService);
    }

    @Test
    void rejectsAnEmptyBatch() {
        webTestClient
                .post()
                .uri("/users/-/local/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("users", List.of()))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    private static User user(String username) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(username);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setUid(username + "-uid");
        spec.setDisplayName(username);
        user.setSpec(spec);
        return user;
    }
}
