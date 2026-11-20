package cn.tolim.pudans.core.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.device.DeviceService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
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
class UserServiceImplPasswordPolicyTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    RoleService roleService;

    @Mock
    DeviceService deviceService;

    @Mock
    ReactiveTransactionManager transactionManager;

    @Mock
    ReactiveSessionRegistry sessionRegistry;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                client,
                passwordEncoder,
                eventPublisher,
                roleService,
                deviceService,
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

        StepVerifier.create(userService.updateWithRawPassword("alice", "a"))
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

        StepVerifier.create(userService.updateWithRawPassword("alice", "1234567"))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();

        verifyNoInteractions(client);
    }

    @Test
    void eightCharacterPasswordIsAccepted() {
        stubSuccessfulUpdate("password");

        StepVerifier.create(userService.updateWithRawPassword("alice", "password"))
                .assertNext(user -> assertThat(user.getMetadata().getName()).isEqualTo("alice"))
                .verifyComplete();
    }

    @Test
    void invalidCharacterIsStillRejected() {
        StepVerifier.create(userService.updateWithRawPassword("alice", "abc-defgh"))
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

        StepVerifier.create(userService.updateWithRawPassword("alice", "password"))
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
