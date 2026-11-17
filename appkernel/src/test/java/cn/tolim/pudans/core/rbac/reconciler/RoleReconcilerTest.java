package cn.tolim.pudans.core.rbac.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.core.rbac.BuiltinEntityDefinitions;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;

@ExtendWith(MockitoExtension.class)
class RoleReconcilerTest {

    @Mock
    ExtensionClient client;

    private RoleReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new RoleReconciler(client);
    }

    private static Role storedRole(String name) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        role.setMetadata(metadata);
        return role;
    }

    @Test
    void restoreReappliesUiPermissionsAnnotationOfBuiltInRoles() {
        var role = storedRole("system-admin-role");
        when(client.fetch(Role.class, "system-admin-role")).thenReturn(Optional.of(role));

        reconciler.reconcile(new Request("system-admin-role"));

        var captor = ArgumentCaptor.forClass(Role.class);
        verify(client).update(captor.capture());
        var annotations = captor.getValue().getMetadata().getAnnotations();
        assertThat(annotations).containsKey(Role.UI_PERMISSIONS_ANNO);
        assertThat(annotations.get(Role.UI_PERMISSIONS_ANNO)).contains("users:manage");
    }

    @Test
    void restoreKeepsAnUntouchedBuiltInRole() {
        when(client.fetch(Role.class, "system-admin-role"))
                .thenReturn(Optional.of(BuiltinEntityDefinitions.systemAdminRole()));

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
