package cn.tolim.pudans.core.rbac.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.rbac.resource.UserGroupBinding;
import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.rbac.SuperAdminResolver;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserGroupBindingReconcilerTest {

    @Mock
    ExtensionClient client;

    @Mock
    SuperAdminResolver superAdminResolver;

    private UserGroupBindingReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new UserGroupBindingReconciler(client, superAdminResolver);
    }

    @Test
    void forgedSuperAdminBindingNameIsRemovedInsteadOfRestored() {
        var binding = binding(
                SuperAdminResolver.bindingName("mallory"),
                "normal-users",
                new String[] {"mallory"});
        when(client.fetch(UserGroupBinding.class, SuperAdminResolver.bindingName("mallory")))
                .thenReturn(Optional.of(binding));

        reconciler.reconcile(new Request(SuperAdminResolver.bindingName("mallory")));

        var updated = captureUpdate();
        assertThat(updated.getMetadata().getDeletionTimestamp()).isNotNull();
        assertThat(updated.getRoleRef().getName()).isEqualTo("normal-users");
        assertThat(updated.getSubjects())
                .extracting(UserGroupBinding.Subject::getName)
                .containsExactly("mallory");
        verify(superAdminResolver, never()).hasOtherCanonicalSuperAdmin(any());
    }

    @Test
    void tamperedSuperAdminsBindingIsRestored() {
        var binding = binding(
                SuperAdminResolver.bindingName("alice"),
                BuiltinEntities.SUPER_ADMINS_GROUP,
                new String[] {"bob"});
        when(client.fetch(UserGroupBinding.class, SuperAdminResolver.bindingName("alice")))
                .thenReturn(Optional.of(binding));
        when(client.fetch(User.class, "alice")).thenReturn(Optional.of(new User()));
        when(superAdminResolver.ownerOf(binding)).thenReturn("alice");
        when(superAdminResolver.hasOtherCanonicalSuperAdmin("alice")).thenReturn(false);

        reconciler.reconcile(new Request(SuperAdminResolver.bindingName("alice")));

        var updated = captureUpdate();
        assertThat(updated.getSubjects())
                .extracting(UserGroupBinding.Subject::getName)
                .containsExactly("alice");
        assertThat(updated.getMetadata().getFinalizers()).contains(MetadataUtil.SYSTEM_FINALIZER);
        assertThat(updated.getMetadata().getDeletionTimestamp()).isNull();
    }

    @Test
    void bindingOutsideTheSuperAdminsGroupIsRemovedForACanonicalSuperAdmin() {
        var binding = binding("team-normal-users-binding", "normal-users", new String[] {"alice"});
        when(client.fetch(UserGroupBinding.class, "team-normal-users-binding"))
                .thenReturn(Optional.of(binding));
        when(superAdminResolver.hasCanonicalBindingBlocking("alice")).thenReturn(true);

        reconciler.reconcile(new Request("team-normal-users-binding"));

        var updated = captureUpdate();
        assertThat(updated.getMetadata().getDeletionTimestamp()).isNotNull();
    }

    @Test
    void missingBindingIsNotUpdated() {
        when(client.fetch(UserGroupBinding.class, "team-normal-users-binding"))
                .thenReturn(Optional.empty());

        reconciler.reconcile(new Request("team-normal-users-binding"));

        verify(client, never()).update(any(UserGroupBinding.class));
    }

    private UserGroupBinding captureUpdate() {
        var captor = ArgumentCaptor.forClass(UserGroupBinding.class);
        verify(client).update(captor.capture());
        return captor.getValue();
    }

    private static UserGroupBinding binding(String name, String groupName, String[] usernames) {
        var binding = new UserGroupBinding();
        var metadata = new Metadata();
        metadata.setName(name);
        binding.setMetadata(metadata);
        binding.setRoleRef(UserGroupBinding.RoleRef.userGroup(groupName));
        binding.setSubjects(new LinkedList<>(java.util.Arrays.stream(usernames)
                .map(UserGroupBinding.Subject::user)
                .toList()));
        return binding;
    }
}
