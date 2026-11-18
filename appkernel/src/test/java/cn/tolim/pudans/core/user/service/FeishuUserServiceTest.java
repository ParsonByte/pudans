package cn.tolim.pudans.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.extension.user.UserConnection;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class FeishuUserServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    UserService userService;

    @Mock
    UserGroupService userGroupService;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    ReactiveTransactionManager transactionManager;

    @Mock
    ReactiveTransaction transaction;

    @Mock
    ParameterSettingGroupService parameterSettingGroupService;

    @Test
    void assignsTheConfiguredDefaultUserGroup() {
        var session = new ParameterSetting.Session();
        session.setDefaultUserGroup("viewers");
        when(parameterSettingGroupService.getSession()).thenReturn(Mono.just(session));
        var group = new UserGroup();
        var metadata = new Metadata();
        metadata.setName("viewers");
        group.setMetadata(metadata);
        when(client.fetch(UserGroup.class, "viewers")).thenReturn(Mono.just(group));
        when(userGroupService.assignUserGroup("bob", "viewers"))
                .thenReturn(Mono.just(new UserGroupBinding()));

        var service = new FeishuUserService(
                client, userService, userGroupService, domainEventPublisher, transactionManager,
                parameterSettingGroupService);

        StepVerifier.create(service.assignDefaultUserGroup("bob")).verifyComplete();

        verify(userGroupService).assignUserGroup("bob", "viewers");
    }

    @Test
    void createsAUserAndConnectionWithoutProvisioningHooks() {
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.empty());
        when(client.create(any(UserConnection.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(userService.createUser(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(userGroupService.assignUserGroup("alice", "normal-users"))
                .thenReturn(Mono.just(new UserGroupBinding()));
        when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        var service = new FeishuUserService(
                client, userService, userGroupService, domainEventPublisher, transactionManager,
                parameterSettingGroupService);
        var data = new FeishuUserService.FeishuUserData(
                "alice", "Alice", "Alice@Example.com", "open-1", "union-1", null);

        StepVerifier.create(service.createFromFeishu(data, "normal-users"))
                .assertNext(user -> {
                    assertThat(user.getMetadata().getName()).isEqualTo("alice");
                    assertThat(user.getSpec().getDisplayName()).isEqualTo("Alice");
                    assertThat(user.getSpec().getEmail()).isEqualTo("alice@example.com");
                    assertThat(user.getSpec().isEmailVerified()).isTrue();
                    assertThat(user.getSpec().getPassword()).isNull();
                })
                .verifyComplete();

        verify(client).create(any(UserConnection.class));
        verify(userGroupService).assignUserGroup("alice", "normal-users");
        verify(domainEventPublisher).publish(eq("user.created"), any(), any());
    }

    @Test
    void bindConnectionCreatesAConnectionForTheCurrentUser() {
        var user = localUser("bob", "Bob", "bob@example.com");
        when(userService.getUser("bob")).thenReturn(Mono.just(user));
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.empty());
        when(client.create(any(UserConnection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var service = new FeishuUserService(
                client, userService, userGroupService, domainEventPublisher, transactionManager,
                parameterSettingGroupService);

        StepVerifier.create(service.bindConnection("bob", "union-1", "Bob", "bob@example.com", null, null))
                .assertNext(connection -> {
                    assertThat(connection.getMetadata().getName()).isEqualTo("bob-feishu");
                    assertThat(connection.getSpec().getRegistrationId()).isEqualTo("feishu");
                    assertThat(connection.getSpec().getUsername()).isEqualTo("bob");
                    assertThat(connection.getSpec().getProviderUserId()).isEqualTo("union-1");
                })
                .verifyComplete();

        verify(client).create(any(UserConnection.class));
    }

    @Test
    void bindConnectionRejectsAnIdentityBoundToAnotherUser() {
        when(userService.getUser("bob")).thenReturn(Mono.just(localUser("bob", "Bob", "bob@example.com")));
        when(client.listAll(eq(UserConnection.class), any(), any()))
                .thenReturn(Flux.just(feishuConnection("alice", "union-1")));
        var service = new FeishuUserService(
                client, userService, userGroupService, domainEventPublisher, transactionManager,
                parameterSettingGroupService);

        StepVerifier.create(service.bindConnection("bob", "union-1", "Bob", "bob@example.com", null, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ServerWebInputException.class)
                        .hasMessageContaining("已绑定到其他用户"))
                .verify();

        verify(client, never()).create(any(UserConnection.class));
    }

    @Test
    void bindConnectionReusesAnExistingConnectionOfTheSameUser() {
        when(userService.getUser("bob")).thenReturn(Mono.just(localUser("bob", "Bob", "bob@example.com")));
        var existing = feishuConnection("bob", "union-1");
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.just(existing));
        var service = new FeishuUserService(
                client, userService, userGroupService, domainEventPublisher, transactionManager,
                parameterSettingGroupService);

        StepVerifier.create(service.bindConnection("bob", "union-1", "Bob", "bob@example.com", null, null))
                .assertNext(connection -> assertThat(connection).isSameAs(existing))
                .verifyComplete();

        verify(client, never()).create(any(UserConnection.class));
    }

    @Test
    void syncProfileUpdatesTheFeishuAvatar() {
        var user = localUser("bob", "Bob", "bob@example.com");
        when(userService.getUser("bob")).thenReturn(Mono.just(user));
        when(client.update(user)).thenReturn(Mono.just(user));
        var service = new FeishuUserService(
                client, userService, userGroupService, domainEventPublisher, transactionManager,
                parameterSettingGroupService);

        StepVerifier.create(service.syncProfile(
                        "bob", "Bob", "bob@example.com", null, "https://feishu.example/avatar.png"))
                .assertNext(updated -> assertThat(updated.getSpec().getAvatar())
                        .isEqualTo("https://feishu.example/avatar.png"))
                .verifyComplete();

        verify(client).update(user);
    }

    @Test
    void syncProfileUpdatesTheFeishuPhone() {
        var user = localUser("bob", "Bob", "bob@example.com");
        when(userService.getUser("bob")).thenReturn(Mono.just(user));
        when(client.update(user)).thenReturn(Mono.just(user));
        var service = new FeishuUserService(
                client, userService, userGroupService, domainEventPublisher, transactionManager,
                parameterSettingGroupService);

        StepVerifier.create(service.syncProfile(
                        "bob", "Bob", "bob@example.com", "+86 13800000000", null))
                .assertNext(updated -> assertThat(updated.getSpec().getPhone())
                        .isEqualTo("+86 13800000000"))
                .verifyComplete();

        verify(client).update(user);
    }

    private static User localUser(String username, String displayName, String email) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(username);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setDisplayName(displayName);
        spec.setEmail(email);
        user.setSpec(spec);
        return user;
    }

    private static UserConnection feishuConnection(String username, String providerUserId) {
        var connection = new UserConnection();
        var metadata = new Metadata();
        metadata.setName(username + "-feishu");
        connection.setMetadata(metadata);
        var spec = new UserConnection.UserConnectionSpec();
        spec.setRegistrationId("feishu");
        spec.setUsername(username);
        spec.setProviderUserId(providerUserId);
        connection.setSpec(spec);
        return connection;
    }
}
