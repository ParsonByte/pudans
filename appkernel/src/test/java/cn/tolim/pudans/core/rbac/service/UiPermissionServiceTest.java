package cn.tolim.pudans.core.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.security.authorization.AuthorityUtils;

@ExtendWith(MockitoExtension.class)
class UiPermissionServiceTest {

    @Mock
    UserGroupService userGroupService;

    @Mock
    RoleService roleService;

    private UiPermissionService service;

    @BeforeEach
    void setUp() {
        service = new UiPermissionService(userGroupService, roleService);
    }

    private static Role role(String name, String uiPermissionsJson) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setAnnotations(uiPermissionsJson == null ? Map.of() : Map.of(Role.UI_PERMISSIONS_ANNO, uiPermissionsJson));
        role.setMetadata(metadata);
        return role;
    }

    @Test
    void superRoleReturnsTheWildcardOnly() {
        when(userGroupService.getRoleNamesByUsername("root"))
                .thenReturn(Flux.just(AuthorityUtils.SUPER_ROLE_NAME));

        StepVerifier.create(service.uiPermissionsOf("root"))
                .assertNext(permissions -> assertThat(permissions).containsExactly("*"))
                .verifyComplete();
    }

    @Test
    void permissionsAreAggregatedAndDeduplicatedAcrossRoles() {
        when(userGroupService.getRoleNamesByUsername("alice"))
                .thenReturn(Flux.just("role-a", "role-b"));
        when(roleService.listPermissions(anySet()))
                .thenReturn(Flux.just(
                        role("role-a", "[\"dashboard:view\", \"users:manage\"]"),
                        role("role-b", "[\"users:manage\", \"audit:view\"]")));

        StepVerifier.create(service.uiPermissionsOf("alice"))
                .assertNext(permissions -> assertThat(permissions)
                        .containsExactly("dashboard:view", "users:manage", "audit:view"))
                .verifyComplete();
    }

    @Test
    void wildcardAnnotationCollapsesEveryTag() {
        when(userGroupService.getRoleNamesByUsername("ops"))
                .thenReturn(Flux.just("role-ops"));
        when(roleService.listPermissions(anySet()))
                .thenReturn(Flux.just(role("role-ops", "[\"audit:view\", \"*\"]")));

        StepVerifier.create(service.uiPermissionsOf("ops"))
                .assertNext(permissions -> assertThat(permissions).containsExactly("*"))
                .verifyComplete();
    }

    @Test
    void rolesWithoutTheAnnotationContributeNothing() {
        when(userGroupService.getRoleNamesByUsername("bob")).thenReturn(Flux.just("role-b"));
        when(roleService.listPermissions(anySet())).thenReturn(Flux.just(role("role-b", null)));

        StepVerifier.create(service.uiPermissionsOf("bob"))
                .assertNext(permissions -> assertThat(permissions).isEmpty())
                .verifyComplete();
    }

    @Test
    void malformedAnnotationsAreSkipped() {
        when(userGroupService.getRoleNamesByUsername("carol")).thenReturn(Flux.just("role-c"));
        when(roleService.listPermissions(anySet()))
                .thenReturn(Flux.just(role("role-c", "not-json"), role("role-c-2", "[\"settings:manage\"]")));

        StepVerifier.create(service.uiPermissionsOf("carol"))
                .assertNext(permissions -> assertThat(permissions).containsExactly("settings:manage"))
                .verifyComplete();
    }

    @Test
    void guestRoleWithTheDisallowAnnotationCannotAccessTheConsole() {
        var metadata = new Metadata();
        metadata.setName("guest");
        metadata.setAnnotations(Map.of(Role.DISALLOW_ACCESS_CONSOLE_ANNO, "true"));
        var guest = new Role();
        guest.setMetadata(metadata);
        when(userGroupService.getRoleNamesByUsername("guest")).thenReturn(Flux.just("guest"));
        when(roleService.list(Set.of("guest"))).thenReturn(Flux.just(guest));

        StepVerifier.create(service.isConsoleAccessDisallowed("guest"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void normalizeCollapsesTheWildcardAndBlankTags() {
        assertThat(UiPermissionService.normalize(List.of("a:read", "", "a:read", "*"))).containsExactly("*");
        assertThat(UiPermissionService.normalize(List.of("a:read", "b:write"))).containsExactlyInAnyOrder("a:read", "b:write");
        assertThat(UiPermissionService.normalize(null)).isEmpty();
    }
}
