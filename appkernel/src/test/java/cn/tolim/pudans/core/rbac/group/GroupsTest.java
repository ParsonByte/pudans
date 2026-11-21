package cn.tolim.pudans.core.rbac.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import cn.tolim.pudans.core.rbac.permission.RbacCache;

@ExtendWith(MockitoExtension.class)
class GroupsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    RbacCache rbacCache;

    @Mock
    BuiltinEntityPolicy builtinEntityPolicy;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private Groups service;

    @BeforeEach
    void setUp() {
        service = new Groups(client, rbacCache, builtinEntityPolicy, domainEventPublisher);
    }

    private static UserGroup group(String name) {
        var group = new UserGroup();
        var metadata = new Metadata();
        metadata.setName(name);
        group.setMetadata(metadata);
        return group;
    }

    // 成员绑定把用户挂到组上，授权绑定把角色挂到组上，两者共用同一张表。
    private void stubBindings() {
        when(client.listAll(eq(UserGroupBinding.class), any(ListOptions.class), any()))
                .thenReturn(Flux.just(
                        UserGroupBinding.membership("alice", "admins"),
                        UserGroupBinding.membership("alice", "admins"),
                        UserGroupBinding.membership("bob", "admins"),
                        UserGroupBinding.grant("admins", "super-role")));
    }

    @Test
    void listUsernamesOfGroupsSkipsGrantsAndDuplicates() {
        stubBindings();

        var usernames = service.listUsernamesOfGroups(List.of("admins")).collectList().block();

        assertThat(usernames).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void listUsernamesOfGroupsReturnsEmptyWithoutQueryForEmptyInput() {
        assertThat(service.listUsernamesOfGroups(List.of()).collectList().block())
                .isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void getGroupsByUsernamesResolvesEachUserOnceAndSkipsGrants() {
        stubBindings();
        when(client.listAll(eq(UserGroup.class), any(ListOptions.class), any()))
                .thenReturn(Flux.just(group("admins")));

        var groups = service.getGroupsByUsernames(List.of("alice", "bob")).block();

        assertThat(groups).containsOnlyKeys("alice", "bob");
        assertThat(groups.get("alice").getMetadata().getName()).isEqualTo("admins");
        assertThat(groups.get("bob").getMetadata().getName()).isEqualTo("admins");
    }

    @Test
    void getGroupsByUsernamesReturnsEmptyMapWithoutQueryForEmptyInput() {
        assertThat(service.getGroupsByUsernames(List.of()).block()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void listMembershipsMapsUsersToTheirGroups() {
        stubBindings();

        var memberships = service.listMemberships().collectList().block();

        assertThat(memberships)
                .containsExactly(
                        new Groups.Membership("alice", "admins"),
                        new Groups.Membership("bob", "admins"));
    }
}
