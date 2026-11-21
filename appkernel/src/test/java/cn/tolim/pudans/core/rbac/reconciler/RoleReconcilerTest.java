package cn.tolim.pudans.core.rbac.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;

@ExtendWith(MockitoExtension.class)
class RoleReconcilerTest {

    @Mock
    ExtensionClient client;

    private BuiltinRoleDefinitions builtinRoleDefinitions;

    private RoleReconciler reconciler;

    @BeforeEach
    void setUp() {
        builtinRoleDefinitions = new BuiltinRoleDefinitions();
        reconciler = new RoleReconciler(client, builtinRoleDefinitions);
    }

    private static Role storedRole(String name) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        role.setMetadata(metadata);
        return role;
    }

    @Test
    void restoreRemovesStaleUiPermissionsAnnotationOfBuiltInRoles() {
        var role = storedRole("system-admin-role");
        role.getMetadata()
                .setAnnotations(
                        new HashMap<>(Map.of(Role.UI_PERMISSIONS_ANNO, "[\"legacy\"]")));
        when(client.fetch(Role.class, "system-admin-role")).thenReturn(Optional.of(role));

        reconciler.reconcile(new Request("system-admin-role"));

        var captor = ArgumentCaptor.forClass(Role.class);
        verify(client).update(captor.capture());
        var annotations = captor.getValue().getMetadata().getAnnotations();
        assertThat(annotations).doesNotContainKey(Role.UI_PERMISSIONS_ANNO);
    }

    @Test
    void systemAdminRoleDelegatesPermissionsToTemplates() {
        var annotations = builtinRoleDefinitions
                .role("system-admin-role")
                .orElseThrow()
                .getMetadata()
                .getAnnotations();

        assertThat(annotations).doesNotContainKey(Role.UI_PERMISSIONS_ANNO);
        assertThat(annotations.get(Role.ROLE_DEPENDENCIES_ANNO))
                .contains("role-template-view-dashboard")
                .contains("role-template-manage-users")
                .contains("role-template-view-traces")
                .contains("role-template-view-replay");
    }

    @Test
    void restoreReappliesDependencyAnnotationOfBuiltInRoles() {
        var canonical = builtinRoleDefinitions.role("system-admin-role").orElseThrow();
        var canonicalDependencies =
                canonical.getMetadata().getAnnotations().get(Role.ROLE_DEPENDENCIES_ANNO);
        assertThat(canonicalDependencies).isNotNull();
        var role = storedRole("system-admin-role");
        role.getMetadata().setAnnotations(new HashMap<>(Map.of(Role.ROLE_DEPENDENCIES_ANNO, "[\"custom-role\"]")));
        when(client.fetch(Role.class, "system-admin-role")).thenReturn(Optional.of(role));

        reconciler.reconcile(new Request("system-admin-role"));

        var captor = ArgumentCaptor.forClass(Role.class);
        verify(client).update(captor.capture());
        assertThat(captor.getValue().getMetadata().getAnnotations())
                .containsEntry(Role.ROLE_DEPENDENCIES_ANNO, canonicalDependencies);
    }

    @Test
    void restoreRemovesUnexpectedDependencyAnnotationOfBuiltInRoles() {
        var role = storedRole("super-role");
        role.getMetadata().setAnnotations(new HashMap<>(Map.of(Role.ROLE_DEPENDENCIES_ANNO, "[\"custom-role\"]")));
        when(client.fetch(Role.class, "super-role")).thenReturn(Optional.of(role));

        reconciler.reconcile(new Request("super-role"));

        var captor = ArgumentCaptor.forClass(Role.class);
        verify(client).update(captor.capture());
        assertThat(captor.getValue().getMetadata().getAnnotations())
                .doesNotContainKey(Role.ROLE_DEPENDENCIES_ANNO);
    }

    @Test
    void restoreKeepsAnUntouchedBuiltInRole() {
        when(client.fetch(Role.class, "system-admin-role"))
                .thenReturn(Optional.of(builtinRoleDefinitions.role("system-admin-role").orElseThrow()));

        reconciler.reconcile(new Request("system-admin-role"));

        verify(client, never()).update(any(Role.class));
    }

    @Test
    void nonBuiltInRolesOnlyGetBookkeepingAnnotations() {
        when(client.fetch(Role.class, "custom-role"))
                .thenAnswer(ignored -> Optional.of(storedRole("custom-role")));

        reconciler.reconcile(new Request("custom-role"));

        var captor = ArgumentCaptor.forClass(Role.class);
        verify(client).update(captor.capture());
        var annotations = captor.getValue().getMetadata().getAnnotations();
        assertThat(annotations)
                .containsEntry(Role.ROLE_DEPENDENCY_RULES, "[]")
                .doesNotContainKey(Role.UI_PERMISSIONS_ANNO);
    }

    @Test
    void missingRolesAreIgnored() {
        when(client.fetch(Role.class, "gone")).thenReturn(Optional.empty());

        var result = reconciler.reconcile(new Request("gone"));

        assertThat(result).isNotNull();
        verify(client, never()).update(any(Role.class));
    }
}
