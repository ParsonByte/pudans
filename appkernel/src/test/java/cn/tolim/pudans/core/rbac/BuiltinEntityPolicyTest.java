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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.core.extension.ai.AiChatMode;
import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.core.extension.ai.AiTeam;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.JsonExtension;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Unstructured;
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

    @Mock
    UserService userService;

    private final BuiltinRoleDefinitions builtinRoleDefinitions = new BuiltinRoleDefinitions();

    private BuiltinEntityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new BuiltinEntityPolicy(
                client, superAdminResolver, builtinRoleDefinitions, rolePermissionPolicy, userService);
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
    void createAiSettingRejectsANonSingletonName() {
        var setting = new AiSetting();
        var metadata = new Metadata();
        metadata.setName("another-setting");
        setting.setMetadata(metadata);

        StepVerifier.create(policy.checkCreate(aiSettingGvk(), setting))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createAiChatModeRejectsBuiltinNames() {
        var mode = new AiChatMode();
        var metadata = new Metadata();
        metadata.setName("fast");
        mode.setMetadata(metadata);
        mode.getSpec().setDisplayName("快速模式");
        mode.getSpec().setRouteGroup("fast-group");

        StepVerifier.create(policy.checkCreate(aiChatModeGvk(), mode))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void updateAiProviderAccountPreservesTheStoredKeyWhenTheRequestOmitsIt() {
        when(client.get(AiProviderAccount.class, "account-1"))
                .thenReturn(Mono.just(aiProviderAccount("enc:stored-key")));
        when(client.fetch(AiProvider.class, "openai-primary")).thenReturn(Mono.just(new AiProvider()));
        var requested = aiProviderAccount(null);

        StepVerifier.create(policy.checkUpdate(aiProviderAccountGvk(), "account-1", requested))
                .verifyComplete();

        assertThat(requested.getSpec().getApiKey()).isEqualTo("enc:stored-key");
    }

    @Test
    void deleteAiSettingIsDenied() {
        var setting = new AiSetting();
        var metadata = new Metadata();
        metadata.setName("ai-setting");
        setting.setMetadata(metadata);

        StepVerifier.create(policy.checkDelete(setting))
                .expectError(AccessDeniedException.class)
                .verify();
    }


    @Test
    void createAiChatModeRejectsAMissingRouteGroup() {
        var mode = new AiChatMode();
        var metadata = new Metadata();
        metadata.setName("custom");
        mode.setMetadata(metadata);
        mode.getSpec().setDisplayName("自定义模式");
        mode.getSpec().setRouteGroup("missing-group");
        when(client.fetch(AiRouteGroup.class, "missing-group")).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(aiChatModeGvk(), mode))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createAiRouteGroupRejectsAMissingMemberModel() {
        var group = new AiRouteGroup();
        var metadata = new Metadata();
        metadata.setName("group-a");
        group.setMetadata(metadata);
        group.getSpec().setDisplayName("路由组 A");
        var member = new AiRouteGroup.Member();
        member.setModel("missing-model");
        group.getSpec().getMembers().add(member);
        when(client.fetch(AiModel.class, "missing-model")).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(aiRouteGroupGvk(), group))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createAiProviderAccountRejectsAMissingProvider() {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName("account-a");
        account.setMetadata(metadata);
        account.getSpec().setProvider("missing-provider");
        when(client.fetch(AiProvider.class, "missing-provider")).thenReturn(Mono.empty());

        StepVerifier.create(policy.checkCreate(aiProviderAccountGvk(), account))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void createAiTeamAcceptsTheSystemIdentity() {
        var team = new AiTeam();
        var metadata = new Metadata();
        metadata.setName("team-a");
        team.setMetadata(metadata);
        team.getSpec().setDisplayName("团队 A");
        team.getSpec().setOwnerUserId(BuiltinEntities.SYSTEM_USER);
        var member = new AiTeam.Member();
        member.setUserId(BuiltinEntities.SYSTEM_USER);
        team.getSpec().getMembers().add(member);

        StepVerifier.create(policy.checkCreate(aiTeamGvk(), team))
                .verifyComplete();
    }

    private static GroupVersionKind aiSettingGvk() {
        return new GroupVersionKind(AiSetting.GROUP, AiSetting.VERSION, AiSetting.KIND);
    }

    private static GroupVersionKind aiChatModeGvk() {
        return new GroupVersionKind(AiChatMode.GROUP, AiChatMode.VERSION, AiChatMode.KIND);
    }

    private static GroupVersionKind aiTeamGvk() {
        return new GroupVersionKind(AiTeam.GROUP, AiTeam.VERSION, AiTeam.KIND);
    }

    private static GroupVersionKind aiRouteGroupGvk() {
        return new GroupVersionKind(AiRouteGroup.GROUP, AiRouteGroup.VERSION, AiRouteGroup.KIND);
    }

    private static GroupVersionKind aiProviderAccountGvk() {
        return new GroupVersionKind(AiProviderAccount.GROUP, AiProviderAccount.VERSION, AiProviderAccount.KIND);
    }

    private static AiProviderAccount aiProviderAccount(String apiKey) {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName("account-1");
        account.setMetadata(metadata);
        account.getSpec().setProvider("openai-primary");
        account.getSpec().setApiKey(apiKey);
        return account;
    }
}
