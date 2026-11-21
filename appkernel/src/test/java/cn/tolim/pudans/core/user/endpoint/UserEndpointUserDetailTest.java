package cn.tolim.pudans.core.user.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.core.infra.utils.PudansTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserEndpointUserDetailTest {

    @Mock
    Accounts accounts;

    @Mock
    AuditLog auditLog;

    @Mock
    Connections userConnectionService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var endpoint = new UserEndpoint(
                null, accounts, null, null, null, auditLog, null, null, null, null, null, userConnectionService);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsTheUsersIdentityConnections() {
        when(accounts.getUser("alice")).thenReturn(Mono.just(user()));
        when(userConnectionService.listConnections("alice")).thenReturn(Flux.just(connection()));

        webTestClient
                .get()
                .uri("/users/alice/connections")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].registrationId")
                .isEqualTo("feishu")
                .jsonPath("$[0].providerUserId")
                .isEqualTo("ou-1")
                .jsonPath("$[0].createdAt")
                .exists();
    }

    @Test
    void returnsDailyActivityForTheRequestedRange() {
        var today = LocalDate.now(PudansTime.BEIJING);
        when(accounts.getUser("alice")).thenReturn(Mono.just(user()));
        when(auditLog.countPerDay(any(), eq("uid-1"), isNull(), isNull()))
                .thenReturn(Mono.just(Map.of(today, 3L)));

        webTestClient
                .get()
                .uri("/users/alice/activity?days=7")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(7)
                .jsonPath("$[6].date")
                .isEqualTo(today.toString())
                .jsonPath("$[6].count")
                .isEqualTo(3);
    }

    private static User user() {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName("alice");
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setUid("uid-1");
        spec.setDisplayName("Alice");
        user.setSpec(spec);
        return user;
    }

    private static UserConnection connection() {
        var connection = new UserConnection();
        var metadata = new Metadata();
        metadata.setName("feishu-1");
        metadata.setCreationTimestamp(Instant.parse("2026-11-01T00:00:00Z"));
        connection.setMetadata(metadata);
        var spec = new UserConnection.UserConnectionSpec();
        spec.setRegistrationId("feishu");
        spec.setUsername("alice");
        spec.setProviderUserId("ou-1");
        spec.setDisplayName("Alice");
        spec.setUpdatedAt(Instant.parse("2026-11-02T00:00:00Z"));
        connection.setSpec(spec);
        return connection;
    }
}
