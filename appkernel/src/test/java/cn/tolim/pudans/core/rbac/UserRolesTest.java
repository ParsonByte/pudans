package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.rbac.group.Groups;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserRolesTest {

    @Mock
    Roles roles;

    @Mock
    Groups groups;

    // 直绑角色与用户组角色必须合并并去重。
    @Test
    void mergesDirectAndGroupRoles() {
        when(roles.getRolesByUsername("alice")).thenReturn(Flux.just("direct", "shared"));
        when(groups.getRoleNamesByUsername("alice")).thenReturn(Flux.just("group", "shared"));

        StepVerifier.create(new UserRoles(roles, groups).list("alice").collectList())
                .assertNext(roleNames -> assertThat(roleNames)
                        .containsExactlyInAnyOrder("direct", "group", "shared"))
                .verifyComplete();
    }

    // 没有直绑角色时只返回用户组角色。
    @Test
    void returnsGroupRolesWithoutDirectBindings() {
        when(roles.getRolesByUsername("bob")).thenReturn(Flux.empty());
        when(groups.getRoleNamesByUsername("bob")).thenReturn(Flux.just("group"));

        StepVerifier.create(new UserRoles(roles, groups).list("bob"))
                .expectNext("group")
                .verifyComplete();
    }
}
