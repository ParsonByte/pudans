package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.rbac.RoleBinding;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class RoleSessionWatcherTest {

    @Mock
    private ReactiveExtensionClient client;

    @Mock
    private Groups groups;

    @Mock
    private SessionInvalidator sessionInvalidator;

    // 角色绑定中的用户和用户组成员都应被失效会话。
    @Test
    void invalidatesUsersAndGroupMembers() {
        var binding = RoleBinding.create("alice", "role-a");
        var group = new RoleBinding.Subject();
        group.setKind(UserGroup.KIND);
        group.setName("ops");
        group.setApiGroup(UserGroup.GROUP);
        binding.getSubjects().add(group);
        when(groups.listUsernamesOfGroups(java.util.List.of("ops"))).thenReturn(Flux.just("bob"));
        when(sessionInvalidator.invalidateAll(any())).thenReturn(Mono.empty());

        new RoleSessionWatcher(client, groups, sessionInvalidator).handle(binding);

        var captor = ArgumentCaptor.forClass(Collection.class);
        verify(sessionInvalidator).invalidateAll(captor.capture());
        assertThat(captor.getValue()).contains("alice", "bob");
    }
}
