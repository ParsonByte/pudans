package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class BuiltinEntityPolicyTest {

    private static final String NAME = "alice";

    @Mock
    ReactiveExtensionClient client;

    @Mock
    SuperAdminResolver superAdminResolver;

    @Mock
    RolePermissionPolicy rolePermissionPolicy;

    private final BuiltinRoleDefinitions builtinRoleDefinitions = new BuiltinRoleDefinitions();

    private BuiltinEntityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new BuiltinEntityPolicy(
                client, superAdminResolver, builtinRoleDefinitions, rolePermissionPolicy);
    }

    private static GroupVersionKind userGvk() {
        return new GroupVersionKind(User.GROUP, User.VERSION, User.KIND);
    }

    private static GroupVersionKind roleGvk() {
        return new GroupVersionKind(Role.GROUP, Role.VERSION, Role.KIND);
    }

    private static GroupVersionKind userGroupGvk() {
        return new GroupVersionKind(UserGroup.GROUP, UserGroup.VERSION, UserGroup.KIND);
    }

    private static GroupVersionKind userGroupBindingGvk() {
        return new GroupVersionKind(
                UserGroupBinding.GROUP, UserGroupBinding.VERSION, UserGroupBinding.KIND);
    }

    private static UserGroup userGroup(String name, String... roleNames) {
        var group = new UserGroup();
        var metadata = new Metadata();
        metadata.setName(name);
        group.setMetadata(metadata);
        group.setDisplayName(name);
        group.setRoleRefs(Arrays.stream(roleNames)
                .map(roleName -> {
                    var roleRef = new UserGroup.RoleRef();
                    roleRef.setName(roleName);
                    return roleRef;
                })
                .toList());
        return group;
    }

    private static UserGroupBinding groupRoleBinding(String groupName, String roleName) {
        var binding = UserGroupBinding.grant(groupName, roleName);
        var metadata = new Metadata();
        metadata.setName(groupName + "-" + roleName + "-binding");
        binding.setMetadata(metadata);
        return binding;
    }

    private static UserGroupBinding membershipBinding(String username, String groupName) {
        var binding = UserGroupBinding.membership(username, groupName);
        var metadata = new Metadata();
        metadata.setName(BuiltinEntities.membershipBindingName(username, groupName));
        binding.setMetadata(metadata);
        return binding;
    }

    private static Role role(String name, String dependencies) {
        var role = new Role();
        var metadata = new Metadata();
        metadata.setName(name);
        if (dependencies != null) {
            metadata.setAnnotations(Map.of(Role.ROLE_DEPENDENCIES_ANNO, dependencies));
        }
        role.setMetadata(metadata);
        role.setRules(List.of());
        return role;
    }

    private static Role template(String name) {
        var role = role(name, null);
        role.getMetadata().setLabels(Map.of(Role.TEMPLATE_LABEL_NAME, "true"));
        return role;
    }

    private static User user(String password, String totpSecret) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(NAME);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setPassword(password);
        spec.setTotpEncryptedSecret(totpSecret);
        user.setSpec(spec);
        return user;
    }

    @Test
    void createRoleWithoutDependenciesPasses() {
        when(rolePermissionPolicy.checkCreate(any())).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(roleGvk(), role("custom-role", null)))
                .verifyComplete();
    }

    @Test
    void createRoleRejectsMissingTemplateDependencies() {
        when(client.fetch(Role.class, "role-template-missing")).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(
                        roleGvk(), role("custom-role", "[ \"role-template-missing\" ]")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不存在");
                })
                .verify();
    }

    @Test
    void createRoleRejectsDependenciesThatAreNotTemplates() {
        when(client.fetch(Role.class, "another-role")).thenReturn(Mono.just(role("another-role", null)));

        StepVerifier.create(policy.checkCreate(
                        roleGvk(), role("custom-role", "[ \"another-role\" ]")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不是角色模板");
                })
                .verify();
    }

    @Test
    void createRoleAllowsTemplateDependencies() {
        when(client.fetch(Role.class, "role-template-view-users"))
                .thenReturn(Mono.just(template("role-template-view-users")));
        when(rolePermissionPolicy.checkCreate(any())).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(
                        roleGvk(), role("custom-role", "[ \"role-template-view-users\" ]")))
                .verifyComplete();
    }

    @Test
    void updateUserRejectsPasswordChangesThroughTheGenericRoute() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var requested = user("attacker-chosen", null);

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("spec.password");
                })
                .verify();
    }

    @Test
    void updateUserRejectsClearingThePassword() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var requested = user(null, null);

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void updateUserRejectsTotpSecretChanges() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", "totp-current")));

        var requested = user("encoded-current", "totp-attacker");

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("totpEncryptedSecret"))
                .verify();
    }

    @Test
    void updateUserAllowsUnrelatedChanges() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", "totp-current")));

        var requested = user("encoded-current", "totp-current");
        requested.getSpec().setBio("updated bio");
        requested.getSpec().setPhone("13800000000");

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, requested)).verifyComplete();
    }

    @Test
    void unstructuredUpdateIsConvertedAndStillGuarded() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var unstructured = new cn.tolim.pudans.contract.extension.Unstructured(Map.of(
                "apiVersion", User.GROUP + "/" + User.VERSION,
                "kind", User.KIND,
                "metadata", Map.of("name", NAME),
                "spec", Map.of("password", "attacker-chosen")));

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, unstructured))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void patchHandlerPathUsesTheSameGuardThroughCheckUpdate() {
        when(client.get(User.class, NAME)).thenReturn(Mono.just(user("encoded-current", null)));

        var patched = user("replaced-through-patch", null);

        StepVerifier.create(policy.checkUpdate(userGvk(), NAME, patched))
                .expectError(AccessDeniedException.class)
                .verify();

        verifyClientLookup();
    }

    private void verifyClientLookup() {
        org.mockito.Mockito.verify(client).get(User.class, NAME);
    }

    @Test
    void createUserGroupRejectsBuiltinRoles() {
        StepVerifier.create(policy.checkCreate(
                        userGroupGvk(), userGroup("custom-group", "normal-role")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("内置角色");
                })
                .verify();
    }

    @Test
    void createUserGroupAllowsCustomRoles() {
        StepVerifier.create(policy.checkCreate(
                        userGroupGvk(), userGroup("custom-group", "custom-role")))
                .verifyComplete();
    }

    @Test
    void updateBuiltinGroupAllowsRetainingBuiltinRolesAndAddingCustomRoles() {
        StepVerifier.create(policy.checkUpdate(
                        userGroupGvk(),
                        "system-admins",
                        userGroup("system-admins", "system-admin-role", "custom-role")))
                .verifyComplete();
    }

    @Test
    void updateBuiltinGroupRejectsRemovingBuiltinRoles() {
        StepVerifier.create(policy.checkUpdate(
                        userGroupGvk(),
                        "system-admins",
                        userGroup("system-admins", "custom-role")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不能移除内置角色");
                })
                .verify();
    }

    @Test
    void updateBuiltinGroupRejectsAddingOtherBuiltinRoles() {
        StepVerifier.create(policy.checkUpdate(
                        userGroupGvk(),
                        "system-admins",
                        userGroup("system-admins", "system-admin-role", "super-role")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不能绑定内置角色");
                })
                .verify();
    }

    @Test
    void updateSuperAdminsGroupIsRejected() {
        StepVerifier.create(policy.checkUpdate(
                        userGroupGvk(), "super-admins", userGroup("super-admins", "super-role")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("超级管理员用户组不可修改");
                })
                .verify();
    }

    @Test
    void bindingSuperRoleToGroupIsRejected() {
        StepVerifier.create(policy.checkCreate(
                        userGroupBindingGvk(), groupRoleBinding("custom-group", "super-role")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("超级管理员角色");
                })
                .verify();
    }

    @Test
    void bindingBuiltinRoleToCustomGroupIsRejected() {
        StepVerifier.create(policy.checkCreate(
                        userGroupBindingGvk(), groupRoleBinding("custom-group", "normal-role")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("内置角色");
                })
                .verify();
    }

    @Test
    void bindingCustomRoleToBuiltinGroupIsAllowed() {
        when(rolePermissionPolicy.checkGrant(any())).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(
                        userGroupBindingGvk(), groupRoleBinding("system-admins", "custom-role")))
                .verifyComplete();
    }

    @Test
    void bindingARoleRequiresTheActorToCoverItsPermissions() {
        when(rolePermissionPolicy.checkGrant(Set.of("custom-role")))
                .thenReturn(Mono.error(new AccessDeniedException(
                        "不能授予超出当前账号权限范围的角色权限。")));

        StepVerifier.create(policy.checkCreate(
                        userGroupBindingGvk(), groupRoleBinding("system-admins", "custom-role")))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void bindingAUserToSystemAdminsRequiresGrantCoverage() {
        when(rolePermissionPolicy.checkGrant(Set.of("system-admin-role")))
                .thenReturn(Mono.error(new AccessDeniedException(
                        "不能授予超出当前账号权限范围的角色权限。")));

        StepVerifier.create(policy.checkCreate(
                        userGroupBindingGvk(), membershipBinding("bob", "system-admins")))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void assigningAUserToSystemAdminsRequiresGrantCoverage() {
        when(superAdminResolver.hasSuperAdminBinding("bob")).thenReturn(Mono.just(false));
        when(rolePermissionPolicy.checkGrant(Set.of("system-admin-role")))
                .thenReturn(Mono.error(new AccessDeniedException(
                        "不能授予超出当前账号权限范围的角色权限。")));

        StepVerifier.create(policy.checkUserGroupAssignment("bob", "system-admins"))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void assigningAUserToSystemAdminsPassesWhenTheActorCoversTheRole() {
        when(superAdminResolver.hasSuperAdminBinding("bob")).thenReturn(Mono.just(false));
        when(rolePermissionPolicy.checkGrant(Set.of("system-admin-role"))).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkUserGroupAssignment("bob", "system-admins"))
                .verifyComplete();
    }

    @Test
    void assigningAUserToANormalGroupSkipsGrantCoverage() {
        when(superAdminResolver.hasSuperAdminBinding("bob")).thenReturn(Mono.just(false));

        StepVerifier.create(policy.checkUserGroupAssignment("bob", "normal-users"))
                .verifyComplete();
    }
}
