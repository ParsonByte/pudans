package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.RoleBinding;
import cn.tolim.pudans.contract.rbac.TempRoleBinding;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.JsonExtension;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        binding.setMetadata(metadataNamed(BuiltinEntities.membershipBindingName(username, groupName)));
        return binding;
    }

    private static UserGroupBinding superAdminNamedBinding(String username, String groupName) {
        var binding = UserGroupBinding.membership(username, groupName);
        binding.setMetadata(metadataNamed(SuperAdminResolver.bindingName(username)));
        return binding;
    }

    private static Metadata metadataNamed(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }

    private static User userNamed(String name) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        user.setSpec(new User.UserSpec());
        return user;
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

        var unstructured = new cn.tolim.pudans.extension.contract.Unstructured(Map.of(
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
        when(rolePermissionPolicy.checkGrant(Set.of("custom-role"))).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(
                        userGroupGvk(), userGroup("custom-group", "custom-role")))
                .verifyComplete();
    }

    @Test
    void updateBuiltinGroupAllowsRetainingBuiltinRolesAndAddingCustomRoles() {
        when(rolePermissionPolicy.checkGrant(Set.of("system-admin-role", "custom-role")))
                .thenReturn(Mono.empty());

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
    void forgedSuperAdminBindingNameIsRejected() {
        StepVerifier.create(policy.checkCreate(
                        userGroupBindingGvk(), superAdminNamedBinding("mallory", "normal-users")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("内置成员绑定");
                })
                .verify();
    }

    @Test
    void superAdminBindingNameWithoutRoleRefIsRejected() {
        var binding = new UserGroupBinding();
        binding.setMetadata(metadataNamed(SuperAdminResolver.bindingName("mallory")));

        StepVerifier.create(policy.checkCreate(userGroupBindingGvk(), binding))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("内置成员绑定");
                })
                .verify();
    }

    @Test
    void creatingASuperAdminsMembershipRequiresAnExistingSuperAdmin() {
        var binding = UserGroupBinding.membership("mallory", BuiltinEntities.SUPER_ADMINS_GROUP);
        binding.setMetadata(metadataNamed(SuperAdminResolver.bindingName("mallory")));
        when(client.fetch(User.class, "mallory")).thenReturn(Mono.just(userNamed("mallory")));
        when(superAdminResolver.isCanonicalSuperAdmin("mallory")).thenReturn(Mono.just(false));

        StepVerifier.create(policy.checkCreate(userGroupBindingGvk(), binding))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("只有已存在的超级管理员");
                })
                .verify();
    }

    @Test
    void deletingASuperAdminsMembershipIsDenied() {
        var binding = UserGroupBinding.membership("alice", BuiltinEntities.SUPER_ADMINS_GROUP);
        binding.setMetadata(metadataNamed(SuperAdminResolver.bindingName("alice")));

        StepVerifier.create(policy.checkBindingMutation(binding, true))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(error.getMessage()).contains("不可更改");
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

    @Test
    void storagePolicyUpdateKeepsCredentialsThatTheRequestOmits() {
        when(client.get(StoragePolicy.class, "storage-1"))
                .thenReturn(Mono.just(storagePolicy("stored-key")));
        var spec = new LinkedHashMap<String, Object>();
        spec.put("displayName", "renamed");
        var update = new Unstructured(Map.of(
                "apiVersion", StoragePolicy.GROUP + "/" + StoragePolicy.VERSION,
                "kind", StoragePolicy.KIND,
                "metadata", Map.of("name", "storage-1"),
                "spec", spec));

        StepVerifier.create(policy.checkUpdate(storagePolicyGvk(), "storage-1", update))
                .verifyComplete();

        assertThat(Unstructured.getNestedValue(update.getData(), "spec", "credentials", "accessKey"))
                .contains("stored-key");
    }

    @Test
    void storagePolicyPatchKeepsCredentialsThatTheRequestOmits() {
        when(client.get(StoragePolicy.class, "storage-1"))
                .thenReturn(Mono.just(storagePolicy("stored-key")));
        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode();
        node.putObject("spec").put("displayName", "renamed");
        var patched = new JsonExtension(mapper, node);

        StepVerifier.create(policy.checkUpdate(storagePolicyGvk(), "storage-1", patched))
                .verifyComplete();

        assertThat(node.at("/spec/credentials/accessKey").asText()).isEqualTo("stored-key");
    }

    private static GroupVersionKind storagePolicyGvk() {
        return new GroupVersionKind(
                StoragePolicy.GROUP, StoragePolicy.VERSION, StoragePolicy.KIND);
    }

    private static StoragePolicy storagePolicy(String accessKey) {
        var storagePolicy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName("storage-1");
        storagePolicy.setMetadata(metadata);
        storagePolicy.getSpec().getCredentials().setAccessKey(accessKey);
        return storagePolicy;
    }

    @Test
    void createUserConnectionForAnotherUserIsDenied() {
        var connection = userConnection("victim");

        StepVerifier.create(authenticated(policy.checkCreate(userConnectionGvk(), connection), NAME))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createUserConnectionForSelfIsAllowed() {
        var connection = userConnection(NAME);

        StepVerifier.create(authenticated(policy.checkCreate(userConnectionGvk(), connection), NAME))
                .verifyComplete();
    }

    @Test
    void roleBindingToSuperRoleIsDenied() {
        var binding = new RoleBinding();
        binding.setMetadata(metadataNamed("alice-super-binding"));
        var roleRef = new RoleBinding.RoleRef();
        roleRef.setKind(Role.KIND);
        roleRef.setName(BuiltinEntities.SUPER_ROLE);
        binding.setRoleRef(roleRef);

        StepVerifier.create(policy.checkCreate(roleBindingGvk(), binding))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void roleNameWithDelimiterIsDenied() {
        StepVerifier.create(policy.checkCreate(roleGvk(), role("x|super-role", null)))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void tempRoleBindingCannotActivateItselfThroughGenericUpdate() {
        var existing = tempRoleBinding(TempRoleBinding.STATUS_PENDING, List.of("viewer"));
        when(client.get(TempRoleBinding.class, "req-1")).thenReturn(Mono.just(existing));
        var requested = tempRoleBinding(TempRoleBinding.STATUS_ACTIVE, List.of("viewer"));

        StepVerifier.create(policy.checkUpdate(tempRoleBindingGvk(), "req-1", requested))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void tempRoleBindingCannotRequestSuperRoleThroughGenericUpdate() {
        var existing = tempRoleBinding(TempRoleBinding.STATUS_PENDING, List.of("viewer"));
        when(client.get(TempRoleBinding.class, "req-1")).thenReturn(Mono.just(existing));
        var requested = tempRoleBinding(TempRoleBinding.STATUS_PENDING, List.of(BuiltinEntities.SUPER_ROLE));

        StepVerifier.create(policy.checkUpdate(tempRoleBindingGvk(), "req-1", requested))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void patCreateForAnotherUserIsDenied() {
        var pat = personalAccessToken("victim");

        StepVerifier.create(authenticated(policy.checkCreate(personalAccessTokenGvk(), pat), NAME))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void patCreateForSelfIsAllowed() {
        var pat = personalAccessToken(NAME);

        StepVerifier.create(authenticated(policy.checkCreate(personalAccessTokenGvk(), pat), NAME))
                .verifyComplete();
    }

    @Test
    void resettingSuperAdminPasswordRequiresSuperRole() {
        when(superAdminResolver.isCanonicalSuperAdmin("root")).thenReturn(Mono.just(true));

        StepVerifier.create(authenticated(
                        policy.checkUserPasswordReset("root"), NAME, BuiltinEntities.SYSTEM_ADMIN_ROLE))
                .expectError(AccessDeniedException.class)
                .verify();

        StepVerifier.create(authenticated(
                        policy.checkUserPasswordReset("root"), NAME, BuiltinEntities.SUPER_ROLE))
                .verifyComplete();
    }

    private static <T> Mono<T> authenticated(Mono<T> mono, String username, String... roles) {
        var authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                username, "n/a", authorities);
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private static UserConnection userConnection(String username) {
        var connection = new UserConnection();
        connection.setMetadata(metadataNamed("connection-1"));
        var spec = new UserConnection.UserConnectionSpec();
        spec.setUsername(username);
        spec.setRegistrationId("feishu");
        spec.setProviderUserId("provider-user-1");
        connection.setSpec(spec);
        return connection;
    }

    private static TempRoleBinding tempRoleBinding(String status, List<String> roleNames) {
        var binding = TempRoleBinding.apply(
                NAME, roleNames, java.time.Instant.now().plusSeconds(600), "reason", "ticket", NAME);
        binding.setMetadata(metadataNamed("req-1"));
        binding.getSpec().setStatus(status);
        return binding;
    }

    private static PersonalAccessToken personalAccessToken(String username) {
        var pat = new PersonalAccessToken();
        pat.setMetadata(metadataNamed("pat-1"));
        pat.getSpec().setName("pat-1");
        pat.getSpec().setUsername(username);
        pat.getSpec().setTokenId("token-1");
        return pat;
    }

    private static GroupVersionKind userConnectionGvk() {
        return new GroupVersionKind(UserConnection.GROUP, UserConnection.VERSION, UserConnection.KIND);
    }

    private static GroupVersionKind roleBindingGvk() {
        return new GroupVersionKind(RoleBinding.GROUP, RoleBinding.VERSION, RoleBinding.KIND);
    }

    private static GroupVersionKind tempRoleBindingGvk() {
        return new GroupVersionKind(
                TempRoleBinding.GROUP, TempRoleBinding.VERSION, TempRoleBinding.KIND);
    }

    private static GroupVersionKind personalAccessTokenGvk() {
        return new GroupVersionKind("security.pudans.tolim.cn", "v1alpha1", PersonalAccessToken.KIND);
    }
}
