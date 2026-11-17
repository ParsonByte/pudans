package cn.tolim.pudans.core.rbac;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.JsonExtension;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.ReservedLabels;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.infra.exception.AccessDeniedException;

@Component
@RequiredArgsConstructor
public class BuiltinEntityPolicy {

    private final ReactiveExtensionClient client;

    private final SuperAdminResolver superAdminResolver;

    public boolean isBuiltinRole(String name) {
        return BuiltinEntities.SUPER_ROLE.equals(name)
                || BuiltinEntities.SYSTEM_ADMIN_ROLE.equals(name)
                || BuiltinEntities.NORMAL_ROLE.equals(name);
    }

    public boolean isBuiltinGroup(String name) {
        return BuiltinEntities.SUPER_ADMINS_GROUP.equals(name)
                || BuiltinEntities.SYSTEM_ADMINS_GROUP.equals(name)
                || BuiltinEntities.NORMAL_USERS_GROUP.equals(name);
    }

    public Mono<Void> checkCreate(GroupVersionKind gvk, Extension extension) {
        var kind = gvk.kind();
        if (Role.KIND.equals(kind) || UserGroup.KIND.equals(kind)) {
            return checkMutation(kind, nameOf(extension));
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
        return Mono.empty();
    }

    public Mono<Void> checkUpdate(GroupVersionKind gvk, String name, Extension extension) {
        var kind = gvk.kind();
        if (Role.KIND.equals(kind) || UserGroup.KIND.equals(kind)) {
            return checkMutation(kind, name);
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
        return Mono.empty();
    }

    public Mono<Void> checkDelete(Extension extension) {
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
                && (cn.tolim.pudans.contract.infra.ParameterSetting.PARAMETER_SETTINGS.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.contract.infra.ParameterSetting.PARAMETER_SETTINGS_DEFAULT.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.contract.infra.ParameterSetting.LEGACY_PARAMETER_SETTINGS.equals(
                                configMap.getMetadata().getName())
                        || cn.tolim.pudans.contract.infra.ParameterSetting.LEGACY_PARAMETER_SETTINGS_DEFAULT.equals(
                                configMap.getMetadata().getName()))) {
            return Mono.error(denied("参数设置配置不可删除。"));
        }
        return Mono.empty();
    }

    public Mono<Void> checkRoleMutation(String name) {
        return Mono.fromRunnable(() -> {
            if (isBuiltinRole(name)) {
                throw denied("内置角色 [" + name + "] 不可修改或删除。");
            }
        });
    }

    public Mono<Void> checkGroupMutation(String name) {
        return Mono.fromRunnable(() -> {
            if (isBuiltinGroup(name)) {
                throw denied("内置用户组 [" + name + "] 不可修改或删除。");
            }
        });
    }

    public Mono<Void> checkUserDeletion(User user) {
        return Mono.defer(() -> superAdminResolver
                .hasSuperAdminBinding(user.getMetadata().getName())
                .flatMap(superAdmin -> Boolean.TRUE.equals(superAdmin)
                        ? Mono.<Void>error(denied("超级管理员账户不可删除。"))
                        : Mono.empty()));
    }

    public Mono<Void> checkBindingMutation(UserGroupBinding binding, boolean deleting) {
        if (binding == null || binding.getRoleRef() == null) {
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
        return denySuperAdminMembership(binding);
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
        });
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
                BuiltinEntityDefinitions.membershipBindingName(subject.getName(), BuiltinEntities.SUPER_ADMINS_GROUP);
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
                    return checkProtectedUserSecrets(user, existing);
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

    private Mono<Void> checkPatCreate(PersonalAccessToken pat) {
        if (pat.getSpec() == null || !pat.getSpec().isSudo()) {
            return Mono.empty();
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> AuthorityUtils.authoritiesToRoles(authentication.getAuthorities())
                        .stream()
                        .anyMatch(role -> BuiltinEntities.SUPER_ROLE.equals(role)
                                || BuiltinEntities.SYSTEM_ADMIN_ROLE.equals(role)))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(denied("只有管理员可以创建带 sudo 能力的令牌。")))
                .then();
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
            return Mono.empty();
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
