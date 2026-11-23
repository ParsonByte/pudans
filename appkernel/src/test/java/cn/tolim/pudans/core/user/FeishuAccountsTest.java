package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.util.List;
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
class FeishuAccountsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    Accounts accounts;

    @Mock
    Connections connections;

    @Mock
    Groups groups;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    ReactiveTransactionManager transactionManager;

    @Mock
    ReactiveTransaction transaction;

    @Mock
    ParameterGroups parameterGroups;

    @Mock
    FeishuClient feishuClient;

    @Test
    void assignsTheConfiguredDefaultUserGroup() {
        var session = new ParameterSetting.Session();
        session.setDefaultUserGroup("viewers");
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        var group = new UserGroup();
        var metadata = new Metadata();
        metadata.setName("viewers");
        group.setMetadata(metadata);
        when(client.fetch(UserGroup.class, "viewers")).thenReturn(Mono.just(group));
        when(groups.assignUserGroup("bob", "viewers"))
                .thenReturn(Mono.just(new UserGroupBinding()));

        var service = service();

        StepVerifier.create(service.assignDefaultUserGroup("bob")).verifyComplete();

        verify(groups).assignUserGroup("bob", "viewers");
    }

    @Test
    void createsAUserAndConnectionWithoutProvisioningHooks() {
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.empty());
        when(connections.createConnection(any(UserConnection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(accounts.createUser(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(groups.assignUserGroup("alice", "normal-users"))
                .thenReturn(Mono.just(new UserGroupBinding()));
        when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        when(feishuClient.fetchContactUser("union-1"))
                .thenReturn(Mono.just(new FeishuClient.FeishuUserInfo(
                        "open-1", "union-1", "Alice", "Alice@Example.com", null, null)));
        var service = service();
        var data = new FeishuAccounts.FeishuUserData(
                "alice", "Manual", "manual@example.com", "open-1", "union-1", null);

        StepVerifier.create(service.createFromFeishu(data, "normal-users"))
                .assertNext(user -> {
                    assertThat(user.getMetadata().getName()).isEqualTo("alice");
                    assertThat(user.getSpec().getDisplayName()).isEqualTo("Alice");
                    assertThat(user.getSpec().getEmail()).isEqualTo("alice@example.com");
                    assertThat(user.getSpec().isEmailVerified()).isTrue();
                    assertThat(user.getSpec().getPassword()).isNull();
                })
                .verifyComplete();

        verify(connections).createConnection(any(UserConnection.class));
        verify(feishuClient).fetchContactUser("union-1");
        verify(groups).assignUserGroup("alice", "normal-users");
        verify(domainEventPublisher).publish(eq("user.created"), any(), any());
    }

    @Test
    void listsFeishuContactsWithImportedState() {
        when(feishuClient.listContactUsers("cursor-1", 50))
                .thenReturn(Mono.just(new FeishuClient.FeishuContactPage(
                        List.of(
                                new FeishuClient.FeishuUserInfo(
                                        "open-1", "union-1", "Alice", "alice@example.com", "+8613800000000", null),
                                new FeishuClient.FeishuUserInfo(
                                        "open-2", "union-2", "Bob", null, null, null)),
                        "cursor-2",
                        true)));
        when(client.listAll(eq(UserConnection.class), any(), any()))
                .thenReturn(Flux.just(feishuConnection("alice", "union-1")));
        when(client.listAll(eq(User.class), any(), any()))
                .thenReturn(Flux.just(localUser("alice", "Alice", "alice@example.com")));

        StepVerifier.create(service().listContacts("cursor-1", 50))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(2);
                    assertThat(page.items().get(0).providerUserId()).isEqualTo("union-1");
                    assertThat(page.items().get(0).email()).isEqualTo("alice@example.com");
                    assertThat(page.items().get(0).imported()).isTrue();
                    assertThat(page.items().get(0).importedUsername()).isEqualTo("alice");
                    assertThat(page.items().get(1).providerUserId()).isEqualTo("union-2");
                    assertThat(page.items().get(1).email()).isNull();
                    assertThat(page.items().get(1).imported()).isFalse();
                    assertThat(page.items().get(1).importedUsername()).isNull();
                    assertThat(page.pageToken()).isEqualTo("cursor-2");
                    assertThat(page.hasMore()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void matchesImportedContactsByOpenIdAsFallback() {
        when(feishuClient.listContactUsers(null, 50))
                .thenReturn(Mono.just(new FeishuClient.FeishuContactPage(
                        List.of(new FeishuClient.FeishuUserInfo(
                                "open-1", "union-1", "Alice", null, null, null)),
                        null,
                        false)));
        when(client.listAll(eq(UserConnection.class), any(), any()))
                .thenReturn(Flux.just(feishuConnection("alice", "open-1")));
        when(client.listAll(eq(User.class), any(), any()))
                .thenReturn(Flux.just(localUser("alice", "Alice", "alice@example.com")));

        StepVerifier.create(service().listContacts(null, 50))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(1);
                    assertThat(page.items().get(0).imported()).isTrue();
                    assertThat(page.items().get(0).importedUsername()).isEqualTo("alice");
                })
                .verifyComplete();
    }

    @Test
    void listsImportedContactsWithTheirCurrentLoginName() {
        when(feishuClient.listContactUsers(null, 50))
                .thenReturn(Mono.just(new FeishuClient.FeishuContactPage(
                        List.of(new FeishuClient.FeishuUserInfo(
                                "open-1", "union-1", "Alice", null, null, null)),
                        null,
                        false)));
        when(client.listAll(eq(UserConnection.class), any(), any()))
                .thenReturn(Flux.just(feishuConnection("alice", "union-1")));
        var user = localUser("alice", "Alice", "alice@example.com");
        user.getSpec().setLoginName("alice.login");
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.just(user));

        StepVerifier.create(service().listContacts(null, 50))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(1);
                    assertThat(page.items().get(0).imported()).isTrue();
                    assertThat(page.items().get(0).importedUsername()).isEqualTo("alice.login");
                })
                .verifyComplete();
    }

    @Test
    void hidesContactsWhoseUserIsPendingDeletion() {
        when(feishuClient.listContactUsers(null, 50))
                .thenReturn(Mono.just(new FeishuClient.FeishuContactPage(
                        List.of(new FeishuClient.FeishuUserInfo(
                                "open-1", "union-1", "Alice", null, null, null)),
                        null,
                        false)));
        when(client.listAll(eq(UserConnection.class), any(), any()))
                .thenReturn(Flux.just(feishuConnection("alice", "union-1")));
        when(client.listAll(eq(User.class), any(), any())).thenReturn(Flux.empty());

        StepVerifier.create(service().listContacts(null, 50))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(1);
                    assertThat(page.items().get(0).imported()).isFalse();
                    assertThat(page.items().get(0).importedUsername()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void createFromFeishuRejectsAMissingProviderId() {
        var data = new FeishuAccounts.FeishuUserData("alice", null, null, null, null, null);

        StepVerifier.create(service().createFromFeishu(data, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ServerWebInputException.class)
                        .hasMessageContaining("union_id"))
                .verify();

        verify(feishuClient, never()).fetchContactUser(any());
    }

    @Test
    void createFromFeishuAcceptsAProfileWithoutEmail() {
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.empty());
        when(connections.createConnection(any(UserConnection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(accounts.createUser(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(groups.assignUserGroup("bob", "normal-users"))
                .thenReturn(Mono.just(new UserGroupBinding()));
        when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(transactionManager.commit(transaction)).thenReturn(Mono.empty());
        when(feishuClient.fetchContactUser("union-2"))
                .thenReturn(Mono.just(new FeishuClient.FeishuUserInfo(
                        "open-2", "union-2", "Bob", null, null, null)));
        var data = new FeishuAccounts.FeishuUserData("bob", null, null, "open-2", "union-2", null);

        StepVerifier.create(service().createFromFeishu(data, "normal-users"))
                .assertNext(user -> {
                    assertThat(user.getSpec().getDisplayName()).isEqualTo("Bob");
                    assertThat(user.getSpec().getEmail()).isNull();
                    assertThat(user.getSpec().isEmailVerified()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void bindConnectionCreatesAConnectionForTheCurrentUser() {
        var user = localUser("bob", "Bob", "bob@example.com");
        when(accounts.getUser("bob")).thenReturn(Mono.just(user));
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.empty());
        when(connections.createConnection(any(UserConnection.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var service = service();

        StepVerifier.create(service.bindConnection("bob", "union-1", "Bob", "bob@example.com", null, null))
                .assertNext(connection -> {
                    assertThat(connection.getSpec().getRegistrationId()).isEqualTo("feishu");
                    assertThat(connection.getSpec().getUsername()).isEqualTo("bob");
                    assertThat(connection.getSpec().getProviderUserId()).isEqualTo("union-1");
                })
                .verifyComplete();

        verify(connections).createConnection(any(UserConnection.class));
    }

    @Test
    void bindConnectionRejectsAnIdentityBoundToAnotherUser() {
        when(accounts.getUser("bob")).thenReturn(Mono.just(localUser("bob", "Bob", "bob@example.com")));
        when(client.listAll(eq(UserConnection.class), any(), any()))
                .thenReturn(Flux.just(feishuConnection("alice", "union-1")));
        var service = service();

        StepVerifier.create(service.bindConnection("bob", "union-1", "Bob", "bob@example.com", null, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ServerWebInputException.class)
                        .hasMessageContaining("已绑定到其他用户"))
                .verify();

        verify(connections, never()).createConnection(any(UserConnection.class));
    }

    @Test
    void bindConnectionReusesAnExistingConnectionOfTheSameUser() {
        when(accounts.getUser("bob")).thenReturn(Mono.just(localUser("bob", "Bob", "bob@example.com")));
        var existing = feishuConnection("bob", "union-1");
        when(client.listAll(eq(UserConnection.class), any(), any())).thenReturn(Flux.just(existing));
        var service = service();

        StepVerifier.create(service.bindConnection("bob", "union-1", "Bob", "bob@example.com", null, null))
                .assertNext(connection -> assertThat(connection).isSameAs(existing))
                .verifyComplete();

        verify(connections, never()).createConnection(any(UserConnection.class));
    }

    @Test
    void syncProfileUpdatesTheFeishuAvatar() {
        var user = localUser("bob", "Bob", "bob@example.com");
        when(accounts.getUser("bob")).thenReturn(Mono.just(user));
        when(client.update(user)).thenReturn(Mono.just(user));
        var service = service();

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
        when(accounts.getUser("bob")).thenReturn(Mono.just(user));
        when(client.update(user)).thenReturn(Mono.just(user));
        var service = service();

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

    private FeishuAccounts service() {
        return new FeishuAccounts(
                client,
                accounts,
                connections,
                groups,
                domainEventPublisher,
                transactionManager,
                parameterGroups,
                feishuClient);
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
