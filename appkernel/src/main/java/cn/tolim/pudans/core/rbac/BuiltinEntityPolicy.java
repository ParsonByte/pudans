package cn.tolim.pudans.core.rbac;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.RoleBinding;
import cn.tolim.pudans.contract.rbac.TempRoleBinding;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.ai.AiAgent;
import cn.tolim.pudans.contract.ai.AiChatMode;
import cn.tolim.pudans.contract.ai.AiInstruction;
import cn.tolim.pudans.contract.ai.AiMcpServer;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.contract.ai.AiSkill;
import cn.tolim.pudans.contract.ai.AiTeam;
import cn.tolim.pudans.core.ai.library.instruction.Instructions;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.library.skill.Skills;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.api.ConfigMap;
import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.extension.api.GroupVersionKind;
import cn.tolim.pudans.extension.api.JsonExtension;
import cn.tolim.pudans.extension.api.MetadataUtil;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.extension.api.ReservedLabels;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.security.api.PersonalAccessToken;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import cn.tolim.pudans.extension.api.Unstructured;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class BuiltinEntityPolicy {

    private final ReactiveExtensionClient client;

    private final SuperAdminResolver superAdminResolver;

    private final BuiltinRoleDefinitions builtinRoleDefinitions;

    private final RolePermissionPolicy rolePermissionPolicy;

    private final Accounts accounts;

    public boolean isBuiltinRole(String name) {
        return BuiltinEntities.SUPER_ROLE.equals(name)
                || BuiltinEntities.SYSTEM_ADMIN_ROLE.equals(name)
                || BuiltinEntities.NORMAL_ROLE.equals(name)
                || BuiltinEntities.AI_OPERATOR_ROLE.equals(name);
    }

    public boolean isBuiltinGroup(String name) {
        return BuiltinEntities.SUPER_ADMINS_GROUP.equals(name)
                || BuiltinEntities.SYSTEM_ADMINS_GROUP.equals(name)
                || BuiltinEntities.NORMAL_USERS_GROUP.equals(name);
    }

    public Mono<Void> checkCreate(GroupVersionKind gvk, Extension extension) {
        var kind = gvk.kind();
        if (StorageFile.KIND.equals(kind)) {
            return Mono.error(denied("存储文件由存储服务管理，请使用文件接口。"));
        }
        if (AiSetting.KIND.equals(kind)) {
            return checkAiSettingName(nameOf(extension));
        }
        if (AiChatMode.KIND.equals(kind)) {
            return denyReservedName(nameOf(extension), Set.of("fast", "pro"), "内置对话模式")
                    .then(convert(extension, AiChatMode.class).flatMap(this::checkAiChatModeReferences));
        }
        if (AiTeam.KIND.equals(kind)) {
            return denyReservedName(nameOf(extension), Set.of("default"), "默认团队")
                    .then(convert(extension, AiTeam.class).flatMap(this::checkAiTeamReferences));
        }
        if (AiAgent.KIND.equals(kind)) {
            return denyReservedName(nameOf(extension), Set.of("default"), "默认 Agent")
                    .then(convert(extension, AiAgent.class).flatMap(this::checkAiAgentReferences));
        }
        if (AiMcpServer.KIND.equals(kind)) {
            return convert(extension, AiMcpServer.class).flatMap(McpServers::validate);
        }
        if (AiSkill.KIND.equals(kind)) {
            return convert(extension, AiSkill.class).flatMap(Skills::validate);
        }
        if (AiInstruction.KIND.equals(kind)) {
            return convert(extension, AiInstruction.class).flatMap(Instructions::validate);
        }
        if (AiProviderAccount.KIND.equals(kind)) {
            return convert(extension, AiProviderAccount.class)
                    .flatMap(this::checkAiProviderAccountReferences);
        }
        if (AiModel.KIND.equals(kind)) {
            return convert(extension, AiModel.class).flatMap(this::checkAiModelReferences);
        }
        if (AiRouteGroup.KIND.equals(kind)) {
            return convert(extension, AiRouteGroup.class).flatMap(this::checkAiRouteGroupReferences);
        }
        if (Role.KIND.equals(kind)) {
            return checkMutation(kind, nameOf(extension))
                    .then(convert(extension, Role.class)
                            .flatMap(role -> checkRoleDependencies(role)
                                    .then(Mono.defer(() -> rolePermissionPolicy.checkCreate(role)))));
        }
        if (UserGroup.KIND.equals(kind)) {
            return checkMutation(kind, nameOf(extension))
                    .then(convert(extension, UserGroup.class).flatMap(this::checkUserGroupRoleRefs));
        }
        if (UserGroupBinding.KIND.equals(kind)) {
            return convert(extension, UserGroupBinding.class)
                    .flatMap(binding -> checkBindingMutation(binding, false));
        }
        if (User.KIND.equals(kind)) {
            return convert(extension, User.class).flatMap(this::checkUserCreate);
        }
        if (PersonalAccessToken.KIND.equals(kind)) {
            return convert(extension, PersonalAccessToken.class).flatMap(this::checkPatCreate);
        }
        if (UserConnection.KIND.equals(kind)) {
            return convert(extension, UserConnection.class).flatMap(this::checkUserConnection);
        }
        if (RoleBinding.KIND.equals(kind)) {
            return convert(extension, RoleBinding.class).flatMap(this::checkRoleBindingGrant);
        }
        if (TempRoleBinding.KIND.equals(kind)) {
            return convert(extension, TempRoleBinding.class).flatMap(this::checkTempRoleBindingCreate);
        }
        return Mono.empty();
    }

    public Mono<Void> checkUpdate(GroupVersionKind gvk, String name, Extension extension) {
        var kind = gvk.kind();
        if (StorageFile.KIND.equals(kind)) {
            return Mono.error(denied("存储文件由存储服务管理，请使用文件接口。"));
        }
        if (StoragePolicy.KIND.equals(kind)) {
            return checkStoragePolicyUpdate(name, extension);
        }
        if (AiProviderAccount.KIND.equals(kind)) {
            return checkAiProviderAccountUpdate(name, extension)
                    .then(convert(extension, AiProviderAccount.class)
                            .flatMap(this::checkAiProviderAccountReferences));
        }
        if (AiSetting.KIND.equals(kind)) {
            return checkAiSettingName(name);
        }
        if (AiChatMode.KIND.equals(kind)) {
            return convert(extension, AiChatMode.class).flatMap(this::checkAiChatModeReferences);
        }
        if (AiTeam.KIND.equals(kind)) {
            return convert(extension, AiTeam.class).flatMap(this::checkAiTeamReferences);
        }
        if (AiAgent.KIND.equals(kind)) {
            return convert(extension, AiAgent.class).flatMap(this::checkAiAgentReferences);
        }
        if (AiMcpServer.KIND.equals(kind)) {
            return convert(extension, AiMcpServer.class).flatMap(McpServers::validate);
        }
        if (AiSkill.KIND.equals(kind)) {
            return convert(extension, AiSkill.class).flatMap(Skills::validate);
        }
        if (AiInstruction.KIND.equals(kind)) {
            return convert(extension, AiInstruction.class).flatMap(Instructions::validate);
        }
        if (AiModel.KIND.equals(kind)) {
            return convert(extension, AiModel.class).flatMap(this::checkAiModelReferences);
        }
        if (AiRouteGroup.KIND.equals(kind)) {
            return convert(extension, AiRouteGroup.class).flatMap(this::checkAiRouteGroupReferences);
        }
        if (Role.KIND.equals(kind)) {
            return checkMutation(kind, name)
                    .then(convert(extension, Role.class)
                            .flatMap(role -> checkRoleDependencies(role)
                                    .then(Mono.defer(() -> rolePermissionPolicy.checkUpdate(role)))));
        }
        if (UserGroup.KIND.equals(kind)) {
            return convert(extension, UserGroup.class)
                    .flatMap(group -> checkUserGroupUpdate(name, group));
        }
        if (UserGroupBinding.KIND.equals(kind)) {
            return convert(extension, UserGroupBinding.class)
                    .flatMap(binding -> checkBindingMutation(binding, false));
        }
        if (User.KIND.equals(kind)) {
            return convert(extension, User.class).flatMap(this::checkUserUpdate);
        }
        if (PersonalAccessToken.KIND.equals(kind)) {
            return convert(extension, PersonalAccessToken.class).flatMap(this::checkPatUpdate);
        }
        if (UserConnection.KIND.equals(kind)) {
            return convert(extension, UserConnection.class).flatMap(this::checkUserConnection);
        }
        if (RoleBinding.KIND.equals(kind)) {
            return convert(extension, RoleBinding.class).flatMap(this::checkRoleBindingGrant);
        }
        if (TempRoleBinding.KIND.equals(kind)) {
            return convert(extension, TempRoleBinding.class).flatMap(this::checkTempRoleBindingUpdate);
        }
        return Mono.empty();
    }

    private Mono<Void> checkStoragePolicyUpdate(String name, Extension extension) {
        return client.get(StoragePolicy.class, name).flatMap(existing -> {
            var stored = existing.getSpec().getCredentials();
            if (stored == null) {
                return Mono.empty();
            }
            if (extension instanceof Unstructured unstructured) {
                preserveUnstructuredCredentials(unstructured, stored);
            } else if (extension instanceof JsonExtension jsonExtension) {
                preserveJsonCredentials(jsonExtension.getInternal(), stored);
            } else if (extension instanceof StoragePolicy policy) {
                preservePolicyCredentials(policy, stored);
            }
            return Mono.empty();
        });
    }

    private Mono<Void> checkAiProviderAccountUpdate(String name, Extension extension) {
        return client.get(AiProviderAccount.class, name).flatMap(existing -> {
            var stored = existing.getSpec() == null ? null : existing.getSpec().getApiKey();
            if (!StringUtils.hasText(stored)) {
                return Mono.empty();
            }
            if (extension instanceof Unstructured unstructured) {
                preserveCredential(ensureChildMap(unstructured.getData(), "spec"), "apiKey", stored);
            } else if (extension instanceof JsonExtension jsonExtension) {
                var root = jsonExtension.getInternal();
                var spec = root.get("spec") instanceof ObjectNode node ? node : root.putObject("spec");
                var current = spec.get("apiKey");
                if (current == null || !StringUtils.hasText(current.asText())) {
                    spec.put("apiKey", stored);
                }
            } else if (extension instanceof AiProviderAccount account) {
                if (account.getSpec() == null) {
                    account.setSpec(new AiProviderAccount.Spec());
                }
                if (!StringUtils.hasText(account.getSpec().getApiKey())) {
                    account.getSpec().setApiKey(stored);
                }
            }
            return Mono.empty();
        });
    }

    private static Mono<Void> checkAiSettingName(String name) {
        if ("ai-setting".equals(name)) {
            return Mono.empty();
        }
        return Mono.error(denied("AI 全局设置只允许名称为 ai-setting。"));
    }

    private static Mono<Void> denyReservedName(String name, Set<String> reserved, String label) {
        if (name == null || !reserved.contains(name)) {
            return Mono.empty();
        }
        return Mono.error(denied(label + " [" + name + "] 由系统内置，不能通过接口创建。"));
    }

    private Mono<Void> checkAiChatModeReferences(AiChatMode mode) {
        var routeGroup = mode.getSpec() == null ? null : mode.getSpec().getRouteGroup();
        return requireReference(AiRouteGroup.class, routeGroup, "对话模式绑定的路由组");
    }

    private Mono<Void> checkAiTeamReferences(AiTeam team) {
        var spec = team.getSpec();
        if (spec == null) {
            return Mono.error(denied("团队配置不能为空。"));
        }
        return requireUserUid(spec.getOwnerUserId(), "团队 owner 用户", true)
                .then(requireReference(AiAgent.class, spec.getDefaultAgent(), "团队默认 Agent", false))
                .then(Flux.fromIterable(spec.getMembers() == null ? List.of() : spec.getMembers())
                        .concatMap(member -> requireUserUid(member.getUserId(), "团队成员用户", true))
                        .then());
    }

    private Mono<Void> checkAiAgentReferences(AiAgent agent) {
        var spec = agent.getSpec();
        if (spec == null) {
            return Mono.error(denied("Agent 配置不能为空。"));
        }
        return requireReference(AiTeam.class, spec.getTeam(), "Agent 所属团队")
                .then(requireUserUid(spec.getOwnerUserId(), "Agent owner 用户", true))
                .then(requireReference(AiRouteGroup.class, spec.getDefaultRouteGroup(), "Agent 默认路由组", false));
    }

    private Mono<Void> checkAiProviderAccountReferences(AiProviderAccount account) {
        var provider = account.getSpec() == null ? null : account.getSpec().getProvider();
        return requireReference(AiProvider.class, provider, "供应商账号所属供应商");
    }

    private Mono<Void> checkAiModelReferences(AiModel model) {
        var spec = model.getSpec();
        if (spec == null) {
            return Mono.error(denied("模型配置不能为空。"));
        }
        if (spec.getModelType() == AiModel.ModelType.EMBEDDING
                && (spec.getEmbeddingDim() == null || spec.getEmbeddingDim() <= 0)) {
            return Mono.error(denied("Embedding 模型必须配置大于零的向量维度。"));
        }
        var provider = spec.getProvider();
        return requireReference(AiProvider.class, provider, "模型所属供应商");
    }

    private Mono<Void> checkAiRouteGroupReferences(AiRouteGroup group) {
        var spec = group.getSpec();
        if (spec == null) {
            return Mono.error(denied("路由组配置不能为空。"));
        }
        return Flux.fromIterable(spec.getMembers() == null ? List.of() : spec.getMembers())
                .concatMap(member -> requireReference(AiModel.class, member.getModel(), "路由组成员模型"))
                .then();
    }

    private Mono<Void> requireUserUid(String uid, String label, boolean required) {
        if (!StringUtils.hasText(uid)) {
            return required ? Mono.error(denied(label + "不能为空。")) : Mono.empty();
        }
        if (BuiltinEntities.SYSTEM_USER.equals(uid)) {
            return Mono.empty();
        }
        return accounts
                .getUserByUid(uid)
                .switchIfEmpty(Mono.error(denied(label + " [" + uid + "] 不存在。")))
                .then();
    }

    private Mono<Void> requireReference(Class<? extends Extension> type, String name, String label) {
        return requireReference(type, name, label, true);
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> requireReference(
            Class<? extends Extension> type, String name, String label, boolean required) {
        if (!StringUtils.hasText(name)) {
            return required ? Mono.error(denied(label + "不能为空。")) : Mono.empty();
        }
        return client.fetch((Class<Extension>) type, name)
                .switchIfEmpty(Mono.error(denied(label + " [" + name + "] 不存在。")))
                .then();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureChildMap(Map<?, ?> parent, String key) {
        var value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        var created = new LinkedHashMap<String, Object>();
        ((Map<String, Object>) parent).put(key, created);
        return created;
    }

    private static void preserveUnstructuredCredentials(Unstructured unstructured, StoragePolicy.Credentials stored) {
        var credentials = ensureChildMap(ensureChildMap(unstructured.getData(), "spec"), "credentials");
        preserveCredential(credentials, "accessKey", stored.getAccessKey());
        preserveCredential(credentials, "secretKey", stored.getSecretKey());
        preserveCredential(credentials, "token", stored.getToken());
        preserveCredential(credentials, "clientSecret", stored.getClientSecret());
    }

    private static void preserveCredential(Map<String, Object> credentials, String field, String stored) {
        var current = credentials.get(field);
        if (StringUtils.hasText(stored) && !(current instanceof String value && StringUtils.hasText(value))) {
            credentials.put(field, stored);
        }
    }

    private static void preserveJsonCredentials(ObjectNode extension, StoragePolicy.Credentials stored) {
        var spec = extension.get("spec") instanceof ObjectNode node ? node : extension.putObject("spec");
        var credentials = spec.get("credentials") instanceof ObjectNode node ? node : spec.putObject("credentials");
        preserveJsonCredential(credentials, "accessKey", stored.getAccessKey());
        preserveJsonCredential(credentials, "secretKey", stored.getSecretKey());
        preserveJsonCredential(credentials, "token", stored.getToken());
        preserveJsonCredential(credentials, "clientSecret", stored.getClientSecret());
    }

    private static void preserveJsonCredential(ObjectNode credentials, String field, String stored) {
        var current = credentials.get(field);
        if (StringUtils.hasText(stored) && (current == null || !StringUtils.hasText(current.asText()))) {
            credentials.put(field, stored);
        }
    }

    private static void preservePolicyCredentials(StoragePolicy policy, StoragePolicy.Credentials stored) {
        var credentials = policy.getSpec().getCredentials();
        if (credentials == null) {
            credentials = new StoragePolicy.Credentials();
            policy.getSpec().setCredentials(credentials);
        }
        if (!StringUtils.hasText(credentials.getAccessKey())) {
            credentials.setAccessKey(stored.getAccessKey());
        }
        if (!StringUtils.hasText(credentials.getSecretKey())) {
            credentials.setSecretKey(stored.getSecretKey());
        }
        if (!StringUtils.hasText(credentials.getToken())) {
            credentials.setToken(stored.getToken());
        }
        if (!StringUtils.hasText(credentials.getClientSecret())) {
            credentials.setClientSecret(stored.getClientSecret());
        }
    }

    public Mono<Void> checkDelete(Extension extension) {
        if (extension instanceof StorageFile) {
            return Mono.error(denied("存储文件由存储服务管理，请使用文件接口。"));
        }
        if (extension instanceof AiSetting) {
            return Mono.error(denied("AI 全局设置不可删除。"));
        }
        if (extension instanceof AiChatMode mode
                && Set.of("fast", "pro").contains(mode.getMetadata().getName())) {
            return Mono.error(denied("内置对话模式不可删除。"));
        }
        if (extension instanceof AiTeam team && "default".equals(team.getMetadata().getName())) {
            return Mono.error(denied("默认团队不可删除。"));
        }
        if (extension instanceof AiAgent agent && "default".equals(agent.getMetadata().getName())) {
            return Mono.error(denied("默认 Agent 不可删除。"));
        }
        if (extension instanceof Role role) {
            return checkRoleMutation(role.getMetadata().getName());
        }
        if (extension instanceof UserGroup group) {
            return checkGroupMutation(group.getMetadata().getName());
        }
        if (extension instanceof User user) {
            return checkUserDeletion(user);
        }
        if (extension instanceof UserGroupBinding binding) {
            return checkBindingMutation(binding, true);
        }
        if (extension instanceof ConfigMap configMap) {
            return checkConfigMapDeletion(configMap);
        }
        return Mono.empty();
    }

    private Mono<Void> checkConfigMapDeletion(ConfigMap configMap) {
        if (configMap.getMetadata() != null
                && (cn.tolim.pudans.infra.api.ParameterSetting.PARAMETER_SETTINGS.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.infra.api.ParameterSetting.PARAMETER_SETTINGS_DEFAULT.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.infra.api.ParameterSetting.LEGACY_PARAMETER_SETTINGS.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.infra.api.ParameterSetting.LEGACY_PARAMETER_SETTINGS_DEFAULT.equals(
                                configMap.getMetadata().getName()))) {
            return Mono.error(denied("参数设置配置不可删除。"));
        }
        return Mono.empty();
    }

    public Mono<Void> checkRoleMutation(String name) {
        return Mono.fromRunnable(() -> {
            if (name != null && (name.contains("|") || name.contains("\u0000"))) {
                throw denied("角色名称包含非法字符。");
            }
            if (isBuiltinRole(name)) {
                throw denied("内置角色 [" + name + "] 不可修改或删除。");
            }
        });
    }

    private Mono<Void> checkRoleDependencies(Role role) {
        var raw = MetadataUtil.nullSafeAnnotations(role).get(Role.ROLE_DEPENDENCIES_ANNO);
        if (!StringUtils.hasText(raw)) {
            return Mono.empty();
        }
        List<String> dependencies;
        try {
            dependencies = JsonUtils.jsonToObject(raw, new TypeReference<>() {});
        } catch (Exception e) {
            return Mono.error(new ServerWebInputException("角色依赖必须是角色名称数组。"));
        }
        if (CollectionUtils.isEmpty(dependencies)) {
            return Mono.empty();
        }
        return Flux.fromIterable(dependencies)
                .filter(StringUtils::hasText)
                .concatMap(dependency -> client.fetch(Role.class, dependency)
                        .switchIfEmpty(Mono.error(denied("角色依赖 [" + dependency + "] 不存在。")))
                        .flatMap(found -> Boolean.parseBoolean(
                                        MetadataUtil.nullSafeLabels(found).get(Role.TEMPLATE_LABEL_NAME))
                                ? Mono.empty()
                                : Mono.error(denied("角色依赖 [" + dependency + "] 不是角色模板。"))))
                .then();
    }

    public Mono<Void> checkGroupMutation(String name) {
        return Mono.fromRunnable(() -> {
            if (isBuiltinGroup(name)) {
                throw denied("内置用户组 [" + name + "] 不可修改或删除。");
            }
        });
    }

    private Mono<Void> checkUserGroupUpdate(String name, UserGroup group) {
        if (BuiltinEntities.SUPER_ADMINS_GROUP.equals(name)) {
            return Mono.error(denied("超级管理员用户组不可修改。"));
        }
        if (builtinRoleDefinitions.group(name).isEmpty() && isBuiltinGroup(name)) {
            return Mono.error(denied("内置用户组 [" + name + "] 不可修改或删除。"));
        }
        return checkUserGroupRoleRefs(group);
    }

    private Mono<Void> checkUserGroupRoleRefs(UserGroup group) {
        return Mono.defer(() -> {
            var name = group.getMetadata() == null ? null : group.getMetadata().getName();
            var canonical = Optional.ofNullable(name).flatMap(builtinRoleDefinitions::group);
            var requested = roleRefNames(group);
            if (canonical.isEmpty()) {
                var builtinRole = requested.stream()
                        .filter(this::isBuiltinRoleDefinition)
                        .findFirst();
                if (builtinRole.isPresent()) {
                    return Mono.error(denied(
                            "自定义用户组不能绑定内置角色 [" + builtinRole.get() + "]。"));
                }
            } else {
                var canonicalNames = canonicalRoleNames(canonical.get());
                var missing = canonicalNames.stream()
                        .filter(roleName -> !requested.contains(roleName))
                        .findFirst();
                if (missing.isPresent()) {
                    return Mono.error(denied(
                            "内置用户组不能移除内置角色 [" + missing.get() + "]。"));
                }
                var disallowed = requested.stream()
                        .filter(this::isBuiltinRoleDefinition)
                        .filter(roleName -> !canonicalNames.contains(roleName))
                        .findFirst();
                if (disallowed.isPresent()) {
                    return Mono.error(denied(
                            "内置用户组不能绑定内置角色 [" + disallowed.get() + "]。"));
                }
            }
            return requested.isEmpty()
                    ? Mono.empty()
                    : rolePermissionPolicy.checkGrant(Set.copyOf(requested));
        });
    }

    private boolean isBuiltinRoleDefinition(String name) {
        return StringUtils.hasText(name) && builtinRoleDefinitions.role(name).isPresent();
    }

    private static List<String> roleRefNames(UserGroup group) {
        var roleRefs = group.getRoleRefs();
        if (CollectionUtils.isEmpty(roleRefs)) {
            return List.of();
        }
        return roleRefs.stream()
                .filter(roleRef -> roleRef != null
                        && Role.KIND.equals(roleRef.getKind())
                        && StringUtils.hasText(roleRef.getName()))
                .map(UserGroup.RoleRef::getName)
                .distinct()
                .toList();
    }

    private static Set<String> canonicalRoleNames(UserGroup group) {
        return Set.copyOf(roleRefNames(group));
    }

    private Mono<Void> checkGroupRoleGrant(UserGroupBinding binding) {
        var roleRef = binding.getRoleRef();
        if (roleRef == null
                || !Role.KIND.equals(roleRef.getKind())
                || !StringUtils.hasText(roleRef.getName())) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            if (BuiltinEntities.SUPER_ROLE.equals(roleRef.getName())) {
                throw denied("不能绑定超级管理员角色。");
            }
            for (var groupName : subjectGroupNames(binding)) {
                if (BuiltinEntities.SUPER_ADMINS_GROUP.equals(groupName)) {
                    throw denied("超级管理员用户组不可修改。");
                }
                var canonical = builtinRoleDefinitions.group(groupName);
                if (canonical.isEmpty()) {
                    if (isBuiltinRoleDefinition(roleRef.getName())) {
                        throw denied("自定义用户组不能绑定内置角色 [" + roleRef.getName() + "]。");
                    }
                    continue;
                }
                if (isBuiltinRoleDefinition(roleRef.getName())
                        && !canonicalRoleNames(canonical.get()).contains(roleRef.getName())) {
                    throw denied("内置用户组不能绑定内置角色 [" + roleRef.getName() + "]。");
                }
            }
        });
    }

    private Mono<Void> checkBindingRoleGrant(UserGroupBinding binding) {
        var roleRef = binding.getRoleRef();
        if (roleRef == null) {
            return Mono.empty();
        }
        if (Role.KIND.equals(roleRef.getKind()) && StringUtils.hasText(roleRef.getName())) {
            return rolePermissionPolicy.checkGrant(Set.of(roleRef.getName()));
        }
        if (UserGroup.KIND.equals(roleRef.getKind())) {
            return checkSystemAdminsGrant(roleRef.getName());
        }
        return Mono.empty();
    }

    private Mono<Void> checkSystemAdminsGrant(String groupName) {
        if (!BuiltinEntities.SYSTEM_ADMINS_GROUP.equals(groupName)) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(builtinRoleDefinitions.group(groupName))
                .flatMap(group -> rolePermissionPolicy.checkGrant(canonicalRoleNames(group)));
    }

    private static List<String> subjectGroupNames(UserGroupBinding binding) {
        var subjects = binding.getSubjects();
        if (CollectionUtils.isEmpty(subjects)) {
            return List.of();
        }
        return subjects.stream()
                .filter(subject -> subject != null
                        && UserGroup.KIND.equals(subject.getKind())
                        && StringUtils.hasText(subject.getName()))
                .map(UserGroupBinding.Subject::getName)
                .distinct()
                .toList();
    }

    public Mono<Void> checkUserDeletion(User user) {
        return Mono.defer(() -> superAdminResolver
                .hasSuperAdminBinding(user.getMetadata().getName())
                .flatMap(superAdmin -> Boolean.TRUE.equals(superAdmin)
                        ? Mono.<Void>error(denied("超级管理员账户不可删除。"))
                        : Mono.empty()));
    }

    public Mono<Void> checkBindingMutation(UserGroupBinding binding, boolean deleting) {
        if (binding == null) {
            return Mono.empty();
        }
        var bindingName = binding.getMetadata() == null ? null : binding.getMetadata().getName();
        // 名字后缀命中即视为超管目标，防止伪造名字被 reconciler 判定为超管绑定
        if (SuperAdminResolver.isSuperAdminBindingName(bindingName) && !isSuperAdminsTarget(binding)) {
            return Mono.error(denied("只允许使用超级管理员的内置成员绑定。"));
        }
        if (binding.getRoleRef() == null) {
            return Mono.empty();
        }
        if (isSuperAdminsTarget(binding)) {
            if (deleting) {
                return Mono.error(denied("超级管理员的成员绑定不可更改。"));
            }
            return checkSuperAdminsBinding(binding);
        }
        if (deleting) {
            return Mono.empty();
        }
        return checkGroupRoleGrant(binding)
                .then(Mono.defer(() -> checkBindingRoleGrant(binding)))
                .then(denySuperAdminMembership(binding));
    }

    public Mono<Void> checkUserGroupAssignment(String username, String groupName) {
        var targetSuperAdmins = BuiltinEntities.SUPER_ADMINS_GROUP.equals(groupName);
        return superAdminResolver.hasSuperAdminBinding(username).flatMap(superAdmin -> {
            if (Boolean.TRUE.equals(superAdmin) && !targetSuperAdmins) {
                return Mono.<Void>error(denied("超级管理员必须保留在 super-admins 用户组中。"));
            }
            if (!Boolean.TRUE.equals(superAdmin) && targetSuperAdmins) {
                return Mono.<Void>error(denied("只有超级管理员可以加入 super-admins 用户组。"));
            }
            return Mono.empty();
        })
                .then(Mono.defer(() -> checkSystemAdminsGrant(groupName)))
                .then(Mono.defer(() -> {
                    if (builtinRoleDefinitions.group(groupName).isPresent()) {
                        return Mono.empty();
                    }
                    return client.fetch(UserGroup.class, groupName)
                            .switchIfEmpty(Mono.error(denied("用户组不存在。")))
                            .flatMap(group -> {
                                var roleNames = roleRefNames(group);
                                return roleNames.isEmpty()
                                        ? Mono.empty()
                                        : rolePermissionPolicy.checkGrant(Set.copyOf(roleNames));
                            });
                }));
    }

    public Mono<Void> checkUserGroupRemoval(String username) {
        return superAdminResolver
                .hasSuperAdminBinding(username)
                .flatMap(superAdmin -> Boolean.TRUE.equals(superAdmin)
                        ? Mono.<Void>error(
                                denied("不能将超级管理员从 super-admins 用户组中移除。"))
                        : Mono.empty());
    }

    public boolean isSuperAdminsTarget(UserGroupBinding binding) {
        var roleRef = binding.getRoleRef();
        return roleRef != null
                && UserGroup.KIND.equals(roleRef.getKind())
                && BuiltinEntities.SUPER_ADMINS_GROUP.equals(roleRef.getName());
    }

    private Mono<Void> checkMutation(String kind, String name) {
        if (Role.KIND.equals(kind)) {
            return checkRoleMutation(name);
        }
        if (UserGroup.KIND.equals(kind)) {
            return checkGroupMutation(name);
        }
        return Mono.empty();
    }

    private Mono<Void> checkSuperAdminsBinding(UserGroupBinding binding) {
        var subjects = binding.getSubjects();
        if (CollectionUtils.isEmpty(subjects) || subjects.size() != 1) {
            return Mono.error(denied("只有超级管理员可以加入 super-admins 用户组。"));
        }
        var subject = subjects.get(0);
        if (!User.KIND.equals(subject.getKind())) {
            return Mono.error(denied("只有超级管理员可以加入 super-admins 用户组。"));
        }
        var expected =
                BuiltinEntities.membershipBindingName(subject.getName(), BuiltinEntities.SUPER_ADMINS_GROUP);
        var name = binding.getMetadata() == null ? null : binding.getMetadata().getName();
        if (!expected.equals(name)) {
            return Mono.error(denied("只允许使用超级管理员的内置成员绑定。"));
        }
        return client.fetch(User.class, subject.getName())
                .switchIfEmpty(Mono.error(denied("只有已存在的用户可以加入 super-admins 用户组。")))
                .filter(user -> !Boolean.TRUE.equals(user.getSpec().getDisabled()))
                .switchIfEmpty(Mono.error(denied("已禁用的用户不能加入 super-admins 用户组。")))
                .then(superAdminResolver.isCanonicalSuperAdmin(subject.getName()))
                .flatMap(canonical -> Boolean.TRUE.equals(canonical)
                        ? Mono.<Void>empty()
                        : Mono.error(denied(
                                "只有已存在的超级管理员可以加入 super-admins 用户组。")));
    }

    private Mono<Void> denySuperAdminMembership(UserGroupBinding binding) {
        var subjects = binding.getSubjects();
        if (CollectionUtils.isEmpty(subjects)) {
            return Mono.empty();
        }
        return Flux.fromIterable(subjects)
                .filter(subject -> User.KIND.equals(subject.getKind()))
                .map(UserGroupBinding.Subject::getName)
                .concatMap(superAdminResolver::hasSuperAdminBinding)
                .filter(Boolean::booleanValue)
                .next()
                .flatMap(ignored -> Mono.<Void>error(
                        denied("超级管理员必须保留在 super-admins 用户组中。")))
                .switchIfEmpty(Mono.empty());
    }

    private Mono<Void> checkUserCreate(User user) {
        if (user.getMetadata() == null || !StringUtils.hasText(user.getMetadata().getName())) {
            return Mono.error(new ServerWebInputException("metadata.name 为必填项。"));
        }
        var reserved = ReservedLabels.of(user.getMetadata().getLabels());
        if (!reserved.isEmpty()) {
            return Mono.error(denied("用户的保留标签为只读。"));
        }
        if (user.getMetadata().getDeletionTimestamp() != null) {
            return Mono.error(denied("metadata.deletionTimestamp 是受保护的字段。"));
        }
        return Mono.empty();
    }

    private Mono<Void> checkUserUpdate(User user) {
        if (user.getMetadata() == null) {
            return Mono.error(new ServerWebInputException("metadata 为必填项。"));
        }
        return client.get(User.class, user.getMetadata().getName())
                .flatMap(existing -> {
                    if (user.getMetadata().getDeletionTimestamp() != null) {
                        return Mono.<Void>error(denied("metadata.deletionTimestamp 是受保护的字段。"));
                    }
                    var requestedFinalizers = nullSafe(user.getMetadata().getFinalizers());
                    if (!requestedFinalizers.isEmpty()
                            && !requestedFinalizers.equals(nullSafe(existing.getMetadata().getFinalizers()))) {
                        return Mono.<Void>error(denied("metadata.finalizers 是受保护的字段。"));
                    }
                    var requestedReserved = ReservedLabels.of(user.getMetadata().getLabels());
                    var existingReserved = ReservedLabels.of(existing.getMetadata().getLabels());
                    if (!requestedReserved.isEmpty() && !requestedReserved.equals(existingReserved)) {
                        return Mono.<Void>error(denied("用户的保留标签为只读。"));
                    }
                    return checkProtectedUserSecrets(user, existing)
                            .then(Mono.defer(() -> checkSuperAdminDisable(user, existing)));
                });
    }

    private Mono<Void> checkProtectedUserSecrets(User requested, User existing) {
        var requestedSpec = requested.getSpec();
        var existingSpec = existing.getSpec();
        if (requestedSpec == null || existingSpec == null) {
            return Mono.empty();
        }
        if (!Objects.equals(requestedSpec.getPassword(), existingSpec.getPassword())) {
            return Mono.<Void>error(denied(
                    "用户的 spec.password 只能通过专用密码接口修改。"));
        }
        if (!Objects.equals(requestedSpec.getTotpEncryptedSecret(), existingSpec.getTotpEncryptedSecret())) {
            return Mono.<Void>error(denied(
                    "用户的 spec.totpEncryptedSecret 只能通过专用认证接口修改。"));
        }
        if (!Objects.equals(requestedSpec.getTotpLastStep(), existingSpec.getTotpLastStep())) {
            return Mono.<Void>error(denied(
                    "用户的 spec.totpLastStep 只能通过专用认证接口修改。"));
        }
        return Mono.empty();
    }

    private Mono<Void> checkUserConnection(UserConnection connection) {
        var spec = connection.getSpec();
        if (spec == null
                || !StringUtils.hasText(spec.getUsername())
                || !StringUtils.hasText(spec.getRegistrationId())
                || !StringUtils.hasText(spec.getProviderUserId())) {
            return Mono.error(denied("身份连接的用户、提供方和提供方用户标识不能为空。"));
        }
        return currentUsername().flatMap(caller -> spec.getUsername().equals(caller)
                ? Mono.empty()
                : Mono.error(denied("只能创建或修改自己的身份连接。")));
    }

    private Mono<Void> checkRoleBindingGrant(RoleBinding binding) {
        var roleRef = binding.getRoleRef();
        if (roleRef == null
                || !Role.KIND.equals(roleRef.getKind())
                || !StringUtils.hasText(roleRef.getName())) {
            return Mono.error(denied("角色绑定必须引用角色。"));
        }
        if (BuiltinEntities.SUPER_ROLE.equals(roleRef.getName())) {
            return Mono.error(denied("不能绑定超级管理员角色。"));
        }
        return rolePermissionPolicy.checkGrant(Set.of(roleRef.getName()));
    }

    private Mono<Void> checkTempRoleBindingCreate(TempRoleBinding binding) {
        var spec = binding.getSpec();
        if (spec == null) {
            return Mono.error(denied("临时角色申请不能为空。"));
        }
        if (!TempRoleBinding.STATUS_PENDING.equals(spec.getStatus())) {
            return Mono.error(denied("临时角色申请创建时必须处于待审批状态。"));
        }
        if (StringUtils.hasText(spec.getApprovedBy()) || spec.getApprovedAt() != null) {
            return Mono.error(denied("不能预设审批信息。"));
        }
        return checkTempRoleNames(spec.getRoleNames());
    }

    private Mono<Void> checkTempRoleBindingUpdate(TempRoleBinding requested) {
        return client.get(TempRoleBinding.class, nameOf(requested)).flatMap(existing -> {
            var next = requested.getSpec();
            var current = existing.getSpec();
            if (next == null || current == null) {
                return Mono.<Void>error(denied("临时角色申请不能为空。"));
            }
            if (!TempRoleBinding.STATUS_PENDING.equals(current.getStatus())) {
                return Mono.<Void>error(denied("只能修改待审批的临时角色申请。"));
            }
            if (!TempRoleBinding.STATUS_PENDING.equals(next.getStatus())) {
                return Mono.<Void>error(denied("临时角色申请状态只能由审批接口修改。"));
            }
            if (!Objects.equals(next.getUsername(), current.getUsername())
                    || !Objects.equals(next.getRequestedBy(), current.getRequestedBy())) {
                return Mono.<Void>error(denied("临时角色申请的申请人不可修改。"));
            }
            if (!Objects.equals(next.getApprovedBy(), current.getApprovedBy())
                    || !Objects.equals(next.getApprovedAt(), current.getApprovedAt())
                    || !Objects.equals(next.getApprovalAuditId(), current.getApprovalAuditId())
                    || !Objects.equals(next.getRevokeAuditId(), current.getRevokeAuditId())) {
                return Mono.<Void>error(denied("不能修改或预设审批信息。"));
            }
            return checkTempRoleNames(next.getRoleNames());
        });
    }

    private static Mono<Void> checkTempRoleNames(List<String> roleNames) {
        if (roleNames == null) {
            return Mono.empty();
        }
        for (var roleName : roleNames) {
            if (!StringUtils.hasText(roleName)) {
                continue;
            }
            if (roleName.contains("|") || roleName.contains("\u0000")) {
                return Mono.error(denied("角色名称包含非法字符。"));
            }
            if (TempRoleBinding.SUPER_ROLE_NAME.equals(roleName)) {
                return Mono.error(denied("不能申请超级管理员角色。"));
            }
        }
        return Mono.empty();
    }

    private Mono<Void> checkSuperAdminDisable(User requested, User existing) {
        var name = requested.getMetadata() == null ? null : requested.getMetadata().getName();
        var requestedSpec = requested.getSpec();
        var existingSpec = existing.getSpec();
        if (requestedSpec == null || existingSpec == null
                || Objects.equals(requestedSpec.getDisabled(), existingSpec.getDisabled())) {
            return Mono.empty();
        }
        return superAdminResolver.isCanonicalSuperAdmin(name).flatMap(superAdmin -> {
            if (!Boolean.TRUE.equals(superAdmin)) {
                return Mono.empty();
            }
            return currentRoles().flatMap(roles -> AuthorityUtils.containsSuperRole(roles)
                    ? Mono.empty()
                    : Mono.error(denied("只有超级管理员可以启用或禁用超级管理员账户。")));
        });
    }

    public Mono<Void> checkUserPasswordReset(String username) {
        return superAdminResolver.isCanonicalSuperAdmin(username).flatMap(superAdmin -> {
            if (!Boolean.TRUE.equals(superAdmin)) {
                return Mono.empty();
            }
            return currentRoles().flatMap(roles -> AuthorityUtils.containsSuperRole(roles)
                    ? Mono.empty()
                    : Mono.error(denied("只有超级管理员可以重置超级管理员密码。")));
        });
    }

    private static Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(denied("未认证。")));
    }

    private static Mono<Set<String>> currentRoles() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> AuthorityUtils.authoritiesToRoles(authentication.getAuthorities()))
                .defaultIfEmpty(Set.of());
    }

    private Mono<Void> checkPatCreate(PersonalAccessToken pat) {
        var spec = pat.getSpec();
        if (spec == null || !StringUtils.hasText(spec.getUsername())) {
            return Mono.error(denied("个人访问令牌必须归属于当前用户。"));
        }
        var finalizers = pat.getMetadata() == null ? Set.<String>of() : nullSafe(pat.getMetadata().getFinalizers());
        if (!finalizers.isEmpty()) {
            return Mono.error(denied("不能为个人访问令牌设置 finalizer。"));
        }
        return currentUsername().flatMap(caller -> {
            if (!caller.equals(spec.getUsername())) {
                return Mono.<Void>error(denied("个人访问令牌只能归属于当前用户。"));
            }
            if (!spec.isSudo()) {
                return Mono.empty();
            }
            return currentRoles().flatMap(roles -> {
                var admin = roles.stream()
                        .anyMatch(role -> BuiltinEntities.SUPER_ROLE.equals(role)
                                || BuiltinEntities.SYSTEM_ADMIN_ROLE.equals(role));
                return admin
                        ? Mono.empty()
                        : Mono.<Void>error(denied("只有管理员可以创建带 sudo 能力的令牌。"));
            });
        });
    }

    private Mono<Void> checkPatUpdate(PersonalAccessToken requested) {
        return client.get(PersonalAccessToken.class, nameOf(requested)).flatMap(existing -> {
            var requestedSpec = requested.getSpec();
            var existingSpec = existing.getSpec();
            if (requestedSpec == null || existingSpec == null) {
                return Mono.empty();
            }
            if (requestedSpec.isSudo() != existingSpec.isSudo()) {
                return Mono.<Void>error(denied("PAT 的 sudo 能力只能由管理员在创建时设置。"));
            }
            if (!Objects.equals(requestedSpec.getUsername(), existingSpec.getUsername())) {
                return Mono.<Void>error(denied("PAT 的所属用户不可修改。"));
            }
            if (!Objects.equals(requestedSpec.getRoles(), existingSpec.getRoles())
                    || !Objects.equals(requestedSpec.getScopes(), existingSpec.getScopes())) {
                return Mono.<Void>error(denied("PAT 的角色和 scope 不可修改。"));
            }
            if (!Objects.equals(requestedSpec.getTokenId(), existingSpec.getTokenId())
                    || !Objects.equals(requestedSpec.getKeyVersion(), existingSpec.getKeyVersion())
                    || requestedSpec.isRevoked() != existingSpec.isRevoked()
                    || !Objects.equals(requestedSpec.getExpiresAt(), existingSpec.getExpiresAt())
                    || !Objects.equals(requestedSpec.getRevokesAt(), existingSpec.getRevokesAt())
                    || !Objects.equals(requestedSpec.getLastUsed(), existingSpec.getLastUsed())) {
                return Mono.<Void>error(denied("PAT 的签名与生命周期字段不可修改。"));
            }
            return currentUsername().flatMap(caller -> caller.equals(existingSpec.getUsername())
                    ? Mono.empty()
                    : Mono.error(denied("只能修改自己的个人访问令牌。")));
        });
    }

    private static Set<String> nullSafe(Set<String> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }

    private static String nameOf(Extension extension) {
        var metadata = extension.getMetadata();
        return metadata == null ? null : metadata.getName();
    }

    private static <T> Mono<T> convert(Extension extension, Class<T> type) {
        if (type.isInstance(extension)) {
            return Mono.just(type.cast(extension));
        }
        return Mono.fromCallable(() -> {
                    if (extension instanceof Unstructured unstructured) {
                        return Unstructured.OBJECT_MAPPER.convertValue(unstructured.getData(), type);
                    }
                    if (extension instanceof JsonExtension jsonExtension) {
                        return jsonExtension.getObjectMapper().convertValue(jsonExtension.getInternal(), type);
                    }
                    return Unstructured.OBJECT_MAPPER.convertValue(extension, type);
                })
                .onErrorMap(
                        error -> new ServerWebInputException(
                                "无法将请求体读取为 " + type.getSimpleName() + "。"));
    }

    private static AccessDeniedException denied(String reason) {
        return new AccessDeniedException(reason);
    }
}
