package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.core.infra.setting.ParameterGroups;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LocalAccountsTest {

    @Mock
    Accounts accounts;

    @Mock
    Groups groups;

    @Mock
    ParameterGroups parameterGroups;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    ReactiveTransactionManager transactionManager;

    @Mock
    ReactiveTransaction transaction;

    @Test
    void createsALocalUserWithAnEncodedPasswordAndGroup() {
        when(accounts.encryptPassword("secret")).thenReturn("encoded");
        when(accounts.createUser(any(User.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(groups.assignUserGroup("alice", "admins"))
                .thenReturn(Mono.just(new UserGroupBinding()));
        when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        var service = new LocalAccounts(
                accounts, groups, parameterGroups, domainEventPublisher, transactionManager);

        StepVerifier.create(service.create(
                        "alice", "Alice", "Alice@Example.com", "secret", "avatar.png", "admins"))
                .assertNext(user -> {
                    assertThat(user.getMetadata().getName()).isEqualTo("alice");
                    assertThat(user.getSpec().getUid()).isNotBlank();
                    assertThat(user.getSpec().getDisplayName()).isEqualTo("Alice");
                    assertThat(user.getSpec().getEmail()).isEqualTo("alice@example.com");
                    assertThat(user.getSpec().getPassword()).isEqualTo("encoded");
                    assertThat(user.getSpec().isEmailVerified()).isTrue();
                    assertThat(user.getSpec().getDisabled()).isFalse();
                })
                .verifyComplete();

        verify(groups).assignUserGroup("alice", "admins");
        verify(domainEventPublisher).publish(eq("user.created"), anyMap(), isNull());
    }

    @Test
    void defaultsTheDisplayNameToTheUsername() {
        when(accounts.encryptPassword("secret")).thenReturn("encoded");
        when(accounts.createUser(any(User.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(groups.assignUserGroup("carol", "admins"))
                .thenReturn(Mono.just(new UserGroupBinding()));
        when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        var service = new LocalAccounts(
                accounts, groups, parameterGroups, domainEventPublisher, transactionManager);

        StepVerifier.create(service.create("carol", "  ", null, "secret", null, "admins"))
                .assertNext(user -> assertThat(user.getSpec().getDisplayName()).isEqualTo("carol"))
                .verifyComplete();
    }

    @Test
    void assignsTheConfiguredDefaultGroup() {
        var session = new ParameterSetting.Session();
        session.setDefaultUserGroup("viewers");
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        when(accounts.encryptPassword("secret")).thenReturn("encoded");
        when(accounts.createUser(any(User.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(groups.assignUserGroup("bob", "viewers"))
                .thenReturn(Mono.just(new UserGroupBinding()));
        when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        var service = new LocalAccounts(
                accounts, groups, parameterGroups, domainEventPublisher, transactionManager);

        StepVerifier.create(service.create("bob", "Bob", null, "secret", null, null))
                .assertNext(user -> assertThat(user.getMetadata().getName()).isEqualTo("bob"))
                .verifyComplete();

        verify(groups).assignUserGroup("bob", "viewers");
    }
}
