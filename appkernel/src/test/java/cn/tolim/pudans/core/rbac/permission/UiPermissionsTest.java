package cn.tolim.pudans.core.rbac.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.security.authorization.AuthorityUtils;

@ExtendWith(MockitoExtension.class)
class UiPermissionsTest {

    @Mock
    UserRoles userRoles;

    @Mock
    Roles roles;

    @Mock
    PermissionDirectory permissionDirectory;

    private UiPermissions service;

    @BeforeEach
    void setUp() {
        service = new UiPermissions(userRoles, roles, permissionDirectory);
        lenient()
                .when(permissionDirectory.expand(anyList()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
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
        when(userRoles.list("root"))
                .thenReturn(Flux.just(AuthorityUtils.SUPER_ROLE_NAME));

        StepVerifier.create(service.uiPermissionsOf("root"))
                .assertNext(permissions -> assertThat(permissions).containsExactly("*"))
                .verifyComplete();
    }

    @Test
    void permissionsAreAggregatedAndDeduplicatedAcrossRoles() {
        when(userRoles.list("alice"))
                .thenReturn(Flux.just("role-a", "role-b"));
        when(roles.listPermissions(anySet()))
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
        when(userRoles.list("ops"))
                .thenReturn(Flux.just("role-ops"));
        when(roles.listPermissions(anySet()))
                .thenReturn(Flux.just(role("role-ops", "[\"audit:view\", \"*\"]")));

        StepVerifier.create(service.uiPermissionsOf("ops"))
                .assertNext(permissions -> assertThat(permissions).containsExactly("*"))
                .verifyComplete();
    }

    @Test
    void rolesWithoutTheAnnotationContributeNothing() {
        when(userRoles.list("bob")).thenReturn(Flux.just("role-b"));
        when(roles.listPermissions(anySet())).thenReturn(Flux.just(role("role-b", null)));

        StepVerifier.create(service.uiPermissionsOf("bob"))
                .assertNext(permissions -> assertThat(permissions).isEmpty())
                .verifyComplete();
    }

    @Test
    void malformedAnnotationsAreSkipped() {
        when(userRoles.list("carol")).thenReturn(Flux.just("role-c"));
        when(roles.listPermissions(anySet()))
                .thenReturn(Flux.just(role("role-c", "not-json"), role("role-c-2", "[\"settings:manage\"]")));

        StepVerifier.create(service.uiPermissionsOf("carol"))
                .assertNext(permissions -> assertThat(permissions).containsExactly("settings:manage"))
                .verifyComplete();
    }

    @Test
    void roleWithTheDisallowAnnotationCannotAccessTheConsole() {
        var metadata = new Metadata();
        metadata.setName("limited-role");
        metadata.setAnnotations(Map.of(Role.DISALLOW_ACCESS_CONSOLE_ANNO, "true"));
        var limitedRole = new Role();
        limitedRole.setMetadata(metadata);
        when(userRoles.list("limited")).thenReturn(Flux.just("limited-role"));
        when(roles.list(Set.of("limited-role"))).thenReturn(Flux.just(limitedRole));

        StepVerifier.create(service.isConsoleAccessDisallowed("limited"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void expandedPermissionsAreAppliedToTheAggregatedTags() {
        when(userRoles.list("dave")).thenReturn(Flux.just("role-d"));
        when(roles.listPermissions(anySet()))
                .thenReturn(Flux.just(role("role-d", "[\"users:manage\"]")));
        when(permissionDirectory.expand(List.of("users:manage")))
                .thenReturn(Mono.just(List.of("users:manage", "users:view", "users:create")));

        StepVerifier.create(service.uiPermissionsOf("dave"))
                .assertNext(permissions -> assertThat(permissions)
                        .containsExactly("users:manage", "users:view", "users:create"))
                .verifyComplete();
    }

    @Test
    void normalizeCollapsesTheWildcardAndBlankTags() {
        assertThat(UiPermissions.normalize(List.of("a:read", "", "a:read", "*"))).containsExactly("*");
        assertThat(UiPermissions.normalize(List.of("a:read", "b:write"))).containsExactlyInAnyOrder("a:read", "b:write");
        assertThat(UiPermissions.normalize(null)).isEmpty();
    }
}
