package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.security.contract.device.DeviceSessions;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.endpoint.UserEndpoint;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.session.ReactiveSessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExtensionAccountsPasswordPolicyTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    Roles roles;

    @Mock
    DeviceSessions deviceSessions;

    @Mock
    ReactiveTransactionManager transactionManager;

    @Mock
    ReactiveSessionRegistry sessionRegistry;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private ExtensionAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new ExtensionAccounts(
                client,
                passwordEncoder,
                eventPublisher,
                roles,
                deviceSessions,
                transactionManager,
                sessionRegistry,
                domainEventPublisher);
    }

    private static User user(String name) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        user.setSpec(new User.UserSpec());
        return user;
    }

    private void stubSuccessfulUpdate(String rawPassword) {
        var user = user("alice");
        lenient().when(client.get(User.class, "alice")).thenReturn(Mono.just(user));
        lenient().when(passwordEncoder.encode(rawPassword)).thenReturn("encoded");
        lenient().when(client.update(any(User.class))).thenReturn(Mono.just(user));
    }

    @Test
    void oneCharacterPasswordIsRejected() {
        stubSuccessfulUpdate("a");

        StepVerifier.create(accounts.updateWithRawPassword("alice", "a"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UnsatisfiedAttributeValueException.class);
                    assertThat(((UnsatisfiedAttributeValueException) error).getStatusCode().value())
                            .isEqualTo(400);
                })
                .verify();

        verifyNoInteractions(client);
    }

    @Test
    void sevenCharacterPasswordIsRejected() {
        stubSuccessfulUpdate("1234567");

        StepVerifier.create(accounts.updateWithRawPassword("alice", "1234567"))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();

        verifyNoInteractions(client);
    }

    @Test
    void eightCharacterPasswordIsAccepted() {
        stubSuccessfulUpdate("password");

        StepVerifier.create(accounts.updateWithRawPassword("alice", "password"))
                .assertNext(user -> assertThat(user.getMetadata().getName()).isEqualTo("alice"))
                .verifyComplete();
    }

    @Test
    void invalidCharacterIsStillRejected() {
        StepVerifier.create(accounts.updateWithRawPassword("alice", "abc-defgh"))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();

        verifyNoInteractions(client);
    }

    @Test
    void samePasswordIsRejected() {
        var user = user("alice");
        user.getSpec().setPassword("stored-hash");
        when(client.get(User.class, "alice")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);

        StepVerifier.create(accounts.updateWithRawPassword("alice", "password"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UnsatisfiedAttributeValueException.class);
                    assertThat(error.getMessage()).contains("新密码不能与旧密码相同");
                })
                .verify();

        verify(client, never()).update(any(User.class));
    }

    @Test
    void passwordDtosDeclareTheSameMinimumLength() {
        var requestComponent = UserEndpoint.PasswordRequest.class.getRecordComponents()[0];
        assertThat(requestComponent.getAccessor().getAnnotation(Size.class).min()).isEqualTo(8);
    }
}
