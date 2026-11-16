package cn.tolim.pudans.core.rbac;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
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
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.infra.exception.AccessDeniedException;

@Component
@RequiredArgsConstructor
public class BuiltinEntityPolicy {

    private static final String RESERVED_LABEL_PREFIX = "pudans.tolim.cn/";

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
            return Mono.error(denied("The parameter settings configuration cannot be deleted."));
        }
        return Mono.empty();
    }

    public Mono<Void> checkRoleMutation(String name) {
        return Mono.fromRunnable(() -> {
            if (isBuiltinRole(name)) {
                throw denied("The built-in role [" + name + "] cannot be modified or deleted.");
            }
        });
    }

    public Mono<Void> checkGroupMutation(String name) {
        return Mono.fromRunnable(() -> {
            if (isBuiltinGroup(name)) {
                throw denied("The built-in user group [" + name + "] cannot be modified or deleted.");
            }
        });
    }

    public Mono<Void> checkUserDeletion(User user) {
        return Mono.defer(() -> superAdminResolver
                .hasSuperAdminBinding(user.getMetadata().getName())
                .flatMap(superAdmin -> Boolean.TRUE.equals(superAdmin)
                        ? Mono.<Void>error(denied("The super administrator account cannot be deleted."))
                        : Mono.empty()));
    }

    public Mono<Void> checkBindingMutation(UserGroupBinding binding, boolean deleting) {
        if (binding == null || binding.getRoleRef() == null) {
            return Mono.empty();
        }
        if (isSuperAdminsTarget(binding)) {
            if (deleting) {
                return Mono.error(denied("The membership binding of the super administrator cannot be changed."));
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
                return Mono.<Void>error(denied("The super administrator must stay in the super-admins group."));
            }
            if (!Boolean.TRUE.equals(superAdmin) && targetSuperAdmins) {
                return Mono.<Void>error(denied("Only the super administrator can join the super-admins group."));
            }
            return Mono.empty();
        });
    }

    public Mono<Void> checkUserGroupRemoval(String username) {
        return superAdminResolver
                .hasSuperAdminBinding(username)
                .flatMap(superAdmin -> Boolean.TRUE.equals(superAdmin)
                        ? Mono.<Void>error(
                                denied("The super administrator cannot be removed from the super-admins group."))
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
            return Mono.error(denied("Only the super administrator can join the super-admins group."));
        }
        var subject = subjects.get(0);
        if (!User.KIND.equals(subject.getKind())) {
            return Mono.error(denied("Only the super administrator can join the super-admins group."));
        }
        var expected =
                BuiltinEntityDefinitions.membershipBindingName(subject.getName(), BuiltinEntities.SUPER_ADMINS_GROUP);
        var name = binding.getMetadata() == null ? null : binding.getMetadata().getName();
        if (!expected.equals(name)) {
            return Mono.error(denied("Only the built-in membership binding of the super administrator is allowed."));
        }
        return client.fetch(User.class, subject.getName())
                .switchIfEmpty(Mono.error(denied("Only an existing user can join the super-admins group.")))
                .filter(user -> !Boolean.TRUE.equals(user.getSpec().getDisabled()))
                .switchIfEmpty(Mono.error(denied("A disabled user cannot join the super-admins group.")))
                .then(superAdminResolver.isCanonicalSuperAdmin(subject.getName()))
                .flatMap(canonical -> Boolean.TRUE.equals(canonical)
                        ? Mono.<Void>empty()
                        : Mono.error(denied(
                                "Only the existing super administrator can join the super-admins group.")));
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
                        denied("The super administrator must stay in the super-admins group.")))
                .switchIfEmpty(Mono.empty());
    }

    private Mono<Void> checkUserCreate(User user) {
        if (user.getMetadata() == null || !StringUtils.hasText(user.getMetadata().getName())) {
            return Mono.error(new ServerWebInputException("metadata.name is required."));
        }
        var reserved = reservedLabels(user.getMetadata().getLabels());
        if (!reserved.isEmpty()) {
            return Mono.error(denied("Reserved labels of a User are read-only."));
        }
        if (user.getMetadata().getDeletionTimestamp() != null) {
            return Mono.error(denied("metadata.deletionTimestamp is a protected field."));
        }
        return Mono.empty();
    }

    private Mono<Void> checkUserUpdate(User user) {
        if (user.getMetadata() == null) {
            return Mono.error(new ServerWebInputException("metadata is required."));
        }
        return client.get(User.class, user.getMetadata().getName())
                .flatMap(existing -> {
                    if (user.getMetadata().getDeletionTimestamp() != null) {
                        return Mono.<Void>error(denied("metadata.deletionTimestamp is a protected field."));
                    }
                    var requestedFinalizers = nullSafe(user.getMetadata().getFinalizers());
                    if (!requestedFinalizers.isEmpty()
                            && !requestedFinalizers.equals(nullSafe(existing.getMetadata().getFinalizers()))) {
                        return Mono.<Void>error(denied("metadata.finalizers is a protected field."));
                    }
                    var requestedReserved = reservedLabels(user.getMetadata().getLabels());
                    var existingReserved = reservedLabels(existing.getMetadata().getLabels());
                    if (!requestedReserved.isEmpty() && !requestedReserved.equals(existingReserved)) {
                        return Mono.<Void>error(denied("Reserved labels of a User are read-only."));
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
                    "spec.password of a User can only be changed through the dedicated password endpoints."));
        }
        if (!Objects.equals(requestedSpec.getTotpEncryptedSecret(), existingSpec.getTotpEncryptedSecret())) {
            return Mono.<Void>error(denied(
                    "spec.totpEncryptedSecret of a User can only be changed through the dedicated authentication endpoints."));
        }
        return Mono.empty();
    }

    private static Set<String> nullSafe(Set<String> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }

    private static Map<String, String> reservedLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Map.of();
        }
        var reserved = new HashMap<String, String>();
        labels.forEach((key, value) -> {
            if (key != null && key.startsWith(RESERVED_LABEL_PREFIX)) {
                reserved.put(key, value);
            }
        });
        return reserved;
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
                                "Unable to read the request body as " + type.getSimpleName() + "."));
    }

    private static AccessDeniedException denied(String reason) {
        return new AccessDeniedException(reason);
    }
}
