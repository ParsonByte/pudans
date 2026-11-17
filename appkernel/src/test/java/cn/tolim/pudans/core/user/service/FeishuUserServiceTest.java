package cn.tolim.pudans.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
}
