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
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.JsonExtension;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.ReservedLabels;
import cn.tolim.pudans.extension.contract.Secret;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
import cn.tolim.pudans.security.contract.RememberMeToken;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import cn.tolim.pudans.extension.contract.Unstructured;
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

    // 判断角色名是否属于受保护的内置角色。
    public boolean isBuiltinRole(String name) {
        return BuiltinEntities.SUPER_ROLE.equals(name)
                || BuiltinEntities.SYSTEM_ADMIN_ROLE.equals(name)
                || BuiltinEntities.NORMAL_ROLE.equals(name);
    }

    // 判断用户组名是否属于内置定义。
    public boolean isBuiltinGroup(String name) {
        return BuiltinEntities.SUPER_ADMINS_GROUP.equals(name)
                || BuiltinEntities.SYSTEM_ADMINS_GROUP.equals(name)
                || BuiltinEntities.NORMAL_USERS_GROUP.equals(name);
    }

    // 按资源类型校验创建，阻止绕过专用接口。
    public Mono<Void> checkCreate(GroupVersionKind gvk, Extension extension) {
        var kind = gvk.kind();
        if (StorageFile.KIND.equals(kind)) {
            return Mono.error(denied("存储文件由存储服务管理，请使用文件接口。"));
        }
        if (UserConnection.KIND.equals(kind)) {
            return Mono.error(denied("身份连接只能通过身份绑定接口维护。"));
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
        if (RoleBinding.KIND.equals(kind)) {
            return convert(extension, RoleBinding.class).flatMap(this::checkRoleBindingGrant);
        }
        if (TempRoleBinding.KIND.equals(kind)) {
            return convert(extension, TempRoleBinding.class).flatMap(this::checkTempRoleBindingCreate);
        }
        return Mono.empty();
    }

    // JSON Patch 不经过专用接口的字段白名单，敏感资源必须拒绝。
    public Mono<Void> checkPatch(GroupVersionKind gvk) {
        var kind = gvk.kind();
        if (User.KIND.equals(kind)
                || PersonalAccessToken.KIND.equals(kind)
                || UserConnection.KIND.equals(kind)
                || RememberMeToken.KIND.equals(kind)) {
            return Mono.error(denied("该资源不支持 JSON Patch，请使用专用接口。"));
        }
        return Mono.empty();
    }

    // 按资源类型校验更新，并保护已存储的敏感字段。
    public Mono<Void> checkUpdate(GroupVersionKind gvk, String name, Extension extension) {
        var kind = gvk.kind();
        if (StorageFile.KIND.equals(kind)) {
            return Mono.error(denied("存储文件由存储服务管理，请使用文件接口。"));
        }
        if (StoragePolicy.KIND.equals(kind)) {
            return checkStoragePolicyUpdate(name, extension);
        }
        if (Webhook.KIND.equals(kind)) {
            return checkWebhookUpdate(name, extension);
        }
        if (Secret.KIND.equals(kind)) {
            return checkSecretUpdate(name, extension);
        }
        if (UserConnection.KIND.equals(kind)) {
            return Mono.error(denied("身份连接只能通过身份绑定接口维护。"));
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
        if (RoleBinding.KIND.equals(kind)) {
            return convert(extension, RoleBinding.class).flatMap(this::checkRoleBindingGrant);
        }
        if (TempRoleBinding.KIND.equals(kind)) {
            return convert(extension, TempRoleBinding.class).flatMap(this::checkTempRoleBindingUpdate);
        }
        return Mono.empty();
    }

    // 更新时沿用已存凭据，避免空值覆盖真实密钥。
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

    // 更新 Webhook 时保留请求中不可见的签名密钥，避免 PUT 静默关闭签名。
    private Mono<Void> checkWebhookUpdate(String name, Extension extension) {
        return client.get(Webhook.class, name).flatMap(existing -> {
            var stored = existing.getSpec() == null ? null : existing.getSpec().getSecret();
            if (!StringUtils.hasText(stored)) {
                return Mono.empty();
            }
            if (extension instanceof Unstructured unstructured) {
                preserveCredential(ensureChildMap(unstructured.getData(), "spec"), "secret", stored);
            } else if (extension instanceof JsonExtension jsonExtension) {
                var root = jsonExtension.getInternal();
                var spec = root.get("spec") instanceof ObjectNode node ? node : root.putObject("spec");
                preserveJsonCredential(spec, "secret", stored);
            } else if (extension instanceof Webhook webhook) {
                if (webhook.getSpec() == null) {
                    webhook.setSpec(new Webhook.Spec());
                }
                if (!StringUtils.hasText(webhook.getSpec().getSecret())) {
                    webhook.getSpec().setSecret(stored);
                }
            }
            return Mono.empty();
        });
    }

    // 密钥更新时补回请求中未提交的值，显式空对象仍表示清空。
    private Mono<Void> checkSecretUpdate(String name, Extension extension) {
        return client.get(Secret.class, name).flatMap(existing -> {
            if (extension instanceof Unstructured unstructured) {
                preserveMissingField(unstructured.getData(), "data", existing.getData());
                preserveMissingField(unstructured.getData(), "stringData", existing.getStringData());
            } else if (extension instanceof JsonExtension jsonExtension) {
                preserveMissingJsonField(jsonExtension, "data", existing.getData());
                preserveMissingJsonField(jsonExtension, "stringData", existing.getStringData());
            } else if (extension instanceof Secret secret) {
                if (secret.getData() == null) {
                    secret.setData(existing.getData());
                }
                if (secret.getStringData() == null) {
                    secret.setStringData(existing.getStringData());
                }
            }
            return Mono.empty();
        });
    }

    // 仅当请求缺少字段时回填已存映射，显式空对象保持不变。
    @SuppressWarnings("unchecked")
    private static void preserveMissingField(Map<?, ?> target, String field, Map<String, ?> stored) {
        if (stored == null || stored.isEmpty() || target.containsKey(field)) {
            return;
        }
        ((Map<String, Object>) target).put(field, stored);
    }

    // 仅当 JSON 请求缺少字段时回填已存映射，显式空对象保持不变。
    private static void preserveMissingJsonField(
            JsonExtension extension, String field, Map<String, ?> stored) {
        if (stored == null || stored.isEmpty() || extension.getInternal().has(field)) {
            return;
        }
        extension.getInternal().set(field, extension.getObjectMapper().valueToTree(stored));
    }

    // 在嵌套配置中定位子映射，不存在时按需创建。
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

    // 把已存凭据补回非结构化请求体，未提交的字段不覆盖。
    private static void preserveUnstructuredCredentials(Unstructured unstructured, StoragePolicy.Credentials stored) {
        var credentials = ensureChildMap(ensureChildMap(unstructured.getData(), "spec"), "credentials");
        preserveCredential(credentials, "accessKey", stored.getAccessKey());
        preserveCredential(credentials, "secretKey", stored.getSecretKey());
        preserveCredential(credentials, "token", stored.getToken());
        preserveCredential(credentials, "clientSecret", stored.getClientSecret());
    }

    // 字段为空时才回填存储值，显式提交优先。
    private static void preserveCredential(Map<String, Object> credentials, String field, String stored) {
        var current = credentials.get(field);
        if (StringUtils.hasText(stored) && !(current instanceof String value && StringUtils.hasText(value))) {
            credentials.put(field, stored);
        }
    }

    // 把已存凭据补回通用请求体，避免密钥丢失。
    private static void preserveJsonCredentials(ObjectNode extension, StoragePolicy.Credentials stored) {
        var spec = extension.get("spec") instanceof ObjectNode node ? node : extension.putObject("spec");
        var credentials = spec.get("credentials") instanceof ObjectNode node ? node : spec.putObject("credentials");
        preserveJsonCredential(credentials, "accessKey", stored.getAccessKey());
        preserveJsonCredential(credentials, "secretKey", stored.getSecretKey());
        preserveJsonCredential(credentials, "token", stored.getToken());
        preserveJsonCredential(credentials, "clientSecret", stored.getClientSecret());
    }

    // 仅当请求体中的字段为空时回填存储值。
    private static void preserveJsonCredential(ObjectNode credentials, String field, String stored) {
        var current = credentials.get(field);
        if (StringUtils.hasText(stored) && (current == null || !StringUtils.hasText(current.asText()))) {
            credentials.put(field, stored);
        }
    }

    // 把已存凭据补回强类型策略，避免空字段覆盖。
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

    // 按资源类型校验删除，内置与受保护对象不可删。
    public Mono<Void> checkDelete(Extension extension) {
        if (extension instanceof StorageFile) {
            return Mono.error(denied("存储文件由存储服务管理，请使用文件接口。"));
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
        if (extension instanceof UserConnection) {
            return Mono.error(denied("身份连接只能通过身份解绑接口删除。"));
        }
        return Mono.empty();
    }

    // 参数设置配置承载全局配置，禁止直接删除。
    private Mono<Void> checkConfigMapDeletion(ConfigMap configMap) {
        if (configMap.getMetadata() != null
                && (cn.tolim.pudans.infra.contract.ParameterSetting.PARAMETER_SETTINGS.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.infra.contract.ParameterSetting.PARAMETER_SETTINGS_DEFAULT.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.infra.contract.ParameterSetting.LEGACY_PARAMETER_SETTINGS.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.infra.contract.ParameterSetting.LEGACY_PARAMETER_SETTINGS_DEFAULT.equals(
                                configMap.getMetadata().getName()))) {
            return Mono.error(denied("参数设置配置不可删除。"));
        }
        return Mono.empty();
    }

    // 拒绝非法字符与内置角色名的修改请求。
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

    // 校验角色依赖均存在且为模板，避免引用失效。
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

    // 拒绝修改内置用户组，保持其定义只读。
    public Mono<Void> checkGroupMutation(String name) {
        return Mono.fromRunnable(() -> {
            if (isBuiltinGroup(name)) {
                throw denied("内置用户组 [" + name + "] 不可修改或删除。");
            }
        });
    }

    // 内置用户组只允许按规范更新，不能移除内置角色。
    private Mono<Void> checkUserGroupUpdate(String name, UserGroup group) {
        if (BuiltinEntities.SUPER_ADMINS_GROUP.equals(name)) {
            return Mono.error(denied("超级管理员用户组不可修改。"));
        }
        if (builtinRoleDefinitions.group(name).isEmpty() && isBuiltinGroup(name)) {
            return Mono.error(denied("内置用户组 [" + name + "] 不可修改或删除。"));
        }
        return checkUserGroupRoleRefs(group);
    }

    // 校验用户组与内置角色的绑定边界，防止越权。
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

    // 判断角色名是否对应内置角色定义。
    private boolean isBuiltinRoleDefinition(String name) {
        return StringUtils.hasText(name) && builtinRoleDefinitions.role(name).isPresent();
    }

    // 提取有效角色引用名并去重，忽略无效引用。
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

    // 返回内置用户组规范定义中的角色名集合。
    private static Set<String> canonicalRoleNames(UserGroup group) {
        return Set.copyOf(roleRefNames(group));
    }

    // 校验用户组绑定授予角色的合法性，阻止提权。
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

    // 校验绑定中的角色或用户组是否在权限范围内。
    private Mono<Void> checkBindingRoleGrant(UserGroupBinding binding) {
        var roleRef = binding.getRoleRef();
        if (roleRef == null) {
            return Mono.empty();
        }
        if (Role.KIND.equals(roleRef.getKind()) && StringUtils.hasText(roleRef.getName())) {
            return rolePermissionPolicy.checkGrant(Set.of(roleRef.getName()));
        }
        if (UserGroup.KIND.equals(roleRef.getKind())) {
            return checkSystemAdminsGrant(roleRef.getName())
                    .then(Mono.defer(() -> checkUserGroupGrant(roleRef.getName())));
        }
        return Mono.empty();
    }

    // 自定义用户组也必须经过角色覆盖校验，防止通过通用绑定接口提权。
    private Mono<Void> checkUserGroupGrant(String groupName) {
        if (!StringUtils.hasText(groupName)
                || BuiltinEntities.SYSTEM_ADMINS_GROUP.equals(groupName)) {
            return Mono.empty();
        }
        var canonical = builtinRoleDefinitions.group(groupName);
        if (canonical.isPresent()) {
            return rolePermissionPolicy.checkGrant(canonicalRoleNames(canonical.get()));
        }
        return client.fetch(UserGroup.class, groupName)
                .switchIfEmpty(Mono.error(denied("用户组不存在。")))
                .flatMap(group -> {
                    var roleNames = roleRefNames(group);
                    return roleNames.isEmpty()
                            ? Mono.empty()
                            : rolePermissionPolicy.checkGrant(Set.copyOf(roleNames));
                });
    }

    // 系统管理员用户组只能按规范角色集合授予。
    private Mono<Void> checkSystemAdminsGrant(String groupName) {
        if (!BuiltinEntities.SYSTEM_ADMINS_GROUP.equals(groupName)) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(builtinRoleDefinitions.group(groupName))
                .flatMap(group -> rolePermissionPolicy.checkGrant(canonicalRoleNames(group)));
    }

    // 提取绑定中有效的用户组主体名并去重。
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

    // 超级管理员账户禁止删除，避免管理入口丢失。
    public Mono<Void> checkUserDeletion(User user) {
        return Mono.defer(() -> superAdminResolver
                .hasSuperAdminBinding(user.getMetadata().getName())
                .flatMap(superAdmin -> Boolean.TRUE.equals(superAdmin)
                        ? Mono.<Void>error(denied("超级管理员账户不可删除。"))
                        : Mono.empty()));
    }

    // 综合校验成员绑定变更，防止伪造或绕过超管约束。
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

    // 校验用户组分配，保证超管归属与授权合法。
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

    // 超级管理员不能被移出超管用户组。
    public Mono<Void> checkUserGroupRemoval(String username) {
        return superAdminResolver
                .hasSuperAdminBinding(username)
                .flatMap(superAdmin -> Boolean.TRUE.equals(superAdmin)
                        ? Mono.<Void>error(
                                denied("不能将超级管理员从 super-admins 用户组中移除。"))
                        : Mono.empty());
    }

    // 判断绑定目标是否为超级管理员用户组。
    public boolean isSuperAdminsTarget(UserGroupBinding binding) {
        var roleRef = binding.getRoleRef();
        return roleRef != null
                && UserGroup.KIND.equals(roleRef.getKind())
                && BuiltinEntities.SUPER_ADMINS_GROUP.equals(roleRef.getName());
    }

    // 按资源类型分派内置对象变更校验。
    private Mono<Void> checkMutation(String kind, String name) {
        if (Role.KIND.equals(kind)) {
            return checkRoleMutation(name);
        }
        if (UserGroup.KIND.equals(kind)) {
            return checkGroupMutation(name);
        }
        return Mono.empty();
    }

    // 超管成员绑定必须只包含一个已存在的规范超管。
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

    // 禁止通过普通绑定为超管追加其他成员资格。
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

    // 校验用户创建，保留标签与受保护字段不可写。
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

    // 校验用户更新，锁定凭据与受保护的元数据。
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

    // 密码与动态口令只能由专用认证接口修改。
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
        // 身份、邮箱与账号状态有专用接口，通用更新不得直接改写。
        if (!Objects.equals(requestedSpec.getUid(), existingSpec.getUid())
                || !Objects.equals(requestedSpec.getEmail(), existingSpec.getEmail())
                || requestedSpec.isEmailVerified() != existingSpec.isEmailVerified()
                || !Objects.equals(requestedSpec.getRegisteredAt(), existingSpec.getRegisteredAt())
                || !Objects.equals(
                        requestedSpec.getTwoFactorAuthEnabled(), existingSpec.getTwoFactorAuthEnabled())
                || !Objects.equals(requestedSpec.getDisabled(), existingSpec.getDisabled())) {
            return Mono.<Void>error(denied("用户身份、邮箱与账号状态只能通过专用接口修改。"));
        }
        return Mono.empty();
    }

    // 角色绑定必须引用合法角色且权限不超过调用方。
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

    // 新申请必须待审批，且不能预设审批信息。
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

    // 只允许修改待审批申请中非审批相关字段。
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

    // 拒绝非法或超管角色名，防止临时提权。
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

    // 只有超级管理员可以启用或禁用超管账户。
    private Mono<Void> checkSuperAdminDisable(User requested, User existing) {
        var name = requested.getMetadata() == null ? null : requested.getMetadata().getName();
        var requestedSpec = requested.getSpec();
        var existingSpec = existing.getSpec();
        if (requestedSpec == null || existingSpec == null
                || Objects.equals(requestedSpec.getDisabled(), existingSpec.getDisabled())) {
            return Mono.empty();
        }
        return checkSuperAdminAccountChange(name, "启用或禁用超级管理员账户");
    }

    // 只有超级管理员可以变更超管账户的安全状态。
    public Mono<Void> checkSuperAdminAccountChange(String username) {
        return checkSuperAdminAccountChange(username, "变更超级管理员账户");
    }

    // 统一校验调用方是否具备超级管理员角色。
    private Mono<Void> checkSuperAdminAccountChange(String username, String action) {
        return superAdminResolver.isCanonicalSuperAdmin(username).flatMap(superAdmin -> {
            if (!Boolean.TRUE.equals(superAdmin)) {
                return Mono.empty();
            }
            return currentRoles().flatMap(roles -> AuthorityUtils.containsSuperRole(roles)
                    ? Mono.empty()
                    : Mono.error(denied("只有超级管理员可以" + action + "。")));
        });
    }

    // 重置超管密码要求调用方本身是超级管理员。
    public Mono<Void> checkUserPasswordReset(String username) {
        return checkSuperAdminAccountChange(username, "重置超级管理员密码");
    }

    // 取当前认证用户名，未认证时直接拒绝。
    private static Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(denied("未认证。")));
    }

    // 取当前认证角色集合，未认证时返回空集合。
    private static Mono<Set<String>> currentRoles() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> AuthorityUtils.authoritiesToRoles(authentication.getAuthorities()))
                .defaultIfEmpty(Set.of());
    }

    // 令牌只能归属当前用户，提权能力仅限管理员。
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

    // 锁定令牌归属、权限与生命周期，仅允许本人修改。
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
            if (!Objects.equals(requestedSpec.getScopeMode(), existingSpec.getScopeMode())
                    || !Objects.equals(requestedSpec.getGateway(), existingSpec.getGateway())) {
                return Mono.<Void>error(denied("PAT 的 scope 模式和网关限制不可修改。"));
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

    // 把可空集合复制为不可空集合，避免共享可变状态。
    private static Set<String> nullSafe(Set<String> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }

    // 安全读取扩展名称，元数据缺失时返回空。
    private static String nameOf(Extension extension) {
        var metadata = extension.getMetadata();
        return metadata == null ? null : metadata.getName();
    }

    // 统一转换为目标类型，解析失败转为请求错误。
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

    // 构造统一的拒绝异常，保持错误语义一致。
    private static AccessDeniedException denied(String reason) {
        return new AccessDeniedException(reason);
    }
}
