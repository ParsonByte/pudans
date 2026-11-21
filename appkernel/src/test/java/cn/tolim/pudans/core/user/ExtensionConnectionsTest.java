package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.event.user.UserConnectionDisconnectedEvent;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExtensionConnectionsTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ApplicationEventPublisher eventPublisher;

    private ExtensionConnections connections;

    @BeforeEach
    void setUp() {
        connections = new ExtensionConnections(client, eventPublisher);
        connections.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAConnectionWithACanonicalName() {
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.empty());
        when(client.create(any(UserConnection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(connections.createConnection(connection("alice", "feishu", "union-1")))
                .assertNext(created -> {
                    assertThat(created.getMetadata().getName()).startsWith("feishu-");
                    assertThat(created.getSpec().getUpdatedAt()).isEqualTo(NOW);
                })
                .verifyComplete();

        verify(client).create(any(UserConnection.class));
    }

    @Test
    void rejectsAConnectionWithoutAProviderUserId() {
        var connection = connection("alice", "feishu", null);

        StepVerifier.create(connections.createConnection(connection))
                .expectError(ServerWebInputException.class)
                .verify();

        verify(client, never()).create(any(UserConnection.class));
    }

    @Test
    void rejectsRemovingTheOnlySignInMethod() {
        var target = connection("alice", "passkey", "credential-1");
        when(client.fetch(UserConnection.class, "passkey-credential-1")).thenReturn(Mono.just(target));
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.just(target));
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user("alice", null)));

        StepVerifier.create(connections.removeConnection("alice", "passkey-credential-1"))
                .expectError(ServerWebInputException.class)
                .verify();

        verify(client, never()).delete(any());
    }

    @Test
    void removesAConnectionWhenAnotherSignInMethodExists() {
        var target = connection("alice", "feishu", "union-1");
        var other = connection("alice", "passkey", "credential-1");
        when(client.fetch(UserConnection.class, "feishu-union-1")).thenReturn(Mono.just(target));
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.just(target, other));
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user("alice", null)));
        when(client.delete(target)).thenReturn(Mono.just(target));

        StepVerifier.create(connections.removeConnection("alice", "feishu-union-1"))
                .assertNext(deleted -> assertThat(deleted).isSameAs(target))
                .verifyComplete();

        verify(eventPublisher).publishEvent(any(UserConnectionDisconnectedEvent.class));
    }

    private static UserConnection connection(String username, String registrationId, String providerUserId) {
        var connection = new UserConnection();
        var metadata = new Metadata();
        metadata.setName(registrationId + "-" + providerUserId);
        connection.setMetadata(metadata);
        var spec = new UserConnection.UserConnectionSpec();
        spec.setUsername(username);
        spec.setRegistrationId(registrationId);
        spec.setProviderUserId(providerUserId);
        connection.setSpec(spec);
        return connection;
    }

    private static User user(String username, String password) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(username);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setPassword(password);
        user.setSpec(spec);
        return user;
    }
}
