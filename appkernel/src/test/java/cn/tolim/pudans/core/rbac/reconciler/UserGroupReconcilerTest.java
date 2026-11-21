package cn.tolim.pudans.core.rbac.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserGroupReconcilerTest {

    @Mock
    ExtensionClient client;

    private final BuiltinRoleDefinitions definitions = new BuiltinRoleDefinitions();

    private UserGroupReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new UserGroupReconciler(client, definitions);
    }

    @Test
    void keepsExtraCustomRolesOnBuiltinGroup() {
        var group = canonicalGroup("system-admins");
        group.getRoleRefs().add(roleRef("custom-role"));
        when(client.fetch(UserGroup.class, "system-admins")).thenReturn(Optional.of(group));

        reconciler.reconcile(new Request("system-admins"));

        verify(client, never()).update(any(UserGroup.class));
    }

    @Test
    void restoresMissingBuiltinRoles() {
        var group = canonicalGroup("system-admins");
        group.setRoleRefs(new ArrayList<>(List.of(roleRef("custom-role"))));
        when(client.fetch(UserGroup.class, "system-admins")).thenReturn(Optional.of(group));

        reconciler.reconcile(new Request("system-admins"));

        var updated = captureUpdate();
        assertThat(updated.getRoleRefs())
                .extracting(UserGroup.RoleRef::getName)
                .containsExactly("system-admin-role", "custom-role");
    }

    @Test
    void removesExtraBuiltinRoles() {
        var group = canonicalGroup("system-admins");
        group.getRoleRefs().add(roleRef("super-role"));
        when(client.fetch(UserGroup.class, "system-admins")).thenReturn(Optional.of(group));

        reconciler.reconcile(new Request("system-admins"));

        var updated = captureUpdate();
        assertThat(updated.getRoleRefs())
                .extracting(UserGroup.RoleRef::getName)
                .containsExactly("system-admin-role");
    }

    @Test
    void removesExtraRolesFromSuperAdminsGroup() {
        var group = canonicalGroup("super-admins");
        group.getRoleRefs().add(roleRef("custom-role"));
        when(client.fetch(UserGroup.class, "super-admins")).thenReturn(Optional.of(group));

        reconciler.reconcile(new Request("super-admins"));

        var updated = captureUpdate();
        assertThat(updated.getRoleRefs())
                .extracting(UserGroup.RoleRef::getName)
                .containsExactly("super-role");
    }

    @Test
    void keepsExtraCustomRolesOnNormalUsersGroup() {
        var group = canonicalGroup("normal-users");
        group.getRoleRefs().add(roleRef("custom-role"));
        when(client.fetch(UserGroup.class, "normal-users")).thenReturn(Optional.of(group));

        reconciler.reconcile(new Request("normal-users"));

        verify(client, never()).update(any(UserGroup.class));
    }

    private UserGroup canonicalGroup(String name) {
        var canonical = definitions.group(name).orElseThrow();
        return Unstructured.OBJECT_MAPPER.convertValue(canonical, UserGroup.class);
    }

    private UserGroup captureUpdate() {
        var captor = ArgumentCaptor.forClass(UserGroup.class);
        verify(client).update(captor.capture());
        return captor.getValue();
    }

    private static UserGroup.RoleRef roleRef(String name) {
        var roleRef = new UserGroup.RoleRef();
        roleRef.setName(name);
        return roleRef;
    }
}
