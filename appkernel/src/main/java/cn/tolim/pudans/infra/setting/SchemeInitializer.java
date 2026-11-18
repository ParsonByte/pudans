package cn.tolim.pudans.infra.setting;

import static cn.tolim.pudans.contract.core.extension.rbac.Role.ROLE_AGGREGATE_LABEL_PREFIX;
import static java.util.Objects.requireNonNullElse;

import cn.tolim.pudans.contract.core.extension.AnnotationSetting;
import cn.tolim.pudans.contract.core.extension.AuthProvider;
import cn.tolim.pudans.contract.core.extension.Device;
import cn.tolim.pudans.contract.core.extension.RememberMeToken;
import cn.tolim.pudans.contract.core.extension.RiskRule;
import cn.tolim.pudans.contract.core.extension.Setting;
import cn.tolim.pudans.contract.core.extension.Webhook;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.RoleBinding;
import cn.tolim.pudans.contract.core.extension.rbac.TempRoleBinding;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.extension.user.UserConnection;
import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.SchemeManager;
import cn.tolim.pudans.contract.extension.Secret;
import cn.tolim.pudans.contract.extension.index.IndexSpecs;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;


@Component
class SchemeInitializer implements SmartLifecycle {

    private final SchemeManager schemeManager;

    private volatile boolean running;

    public SchemeInitializer(SchemeManager schemeManager) {
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        schemeManager.register(Role.class, is -> is.add(
                IndexSpecs.<Role, String>multi("labels.aggregateToRoles", String.class)
                        .indexFunc(role -> Optional.ofNullable(role.getMetadata().getLabels())
                                .map(labels -> labels.keySet().stream()
                                        .filter(key -> key.startsWith(ROLE_AGGREGATE_LABEL_PREFIX))
                                        .filter(key -> Boolean.parseBoolean(labels.get(key)))
                                        .map(key -> StringUtils.removeStart(key, ROLE_AGGREGATE_LABEL_PREFIX))
                                        .collect(Collectors.toSet()))
                                .orElseGet(Set::of))));

        schemeManager.register(RoleBinding.class, is -> {
            is.add(IndexSpecs.<RoleBinding, String>single("roleRef.name", String.class)
                    .indexFunc(roleBinding -> roleBinding.getRoleRef().getName()));
            is.add(IndexSpecs.<RoleBinding, String>multi("subjects", String.class)
                    .indexFunc(roleBinding -> roleBinding.getSubjects().stream()
                            .map(RoleBinding.Subject::toString)
                            .collect(Collectors.toSet())));
        });

        schemeManager.register(UserGroup.class, is -> is.add(
                IndexSpecs.<UserGroup, String>single("displayName", String.class)
                        .indexFunc(UserGroup::getDisplayName)));

        schemeManager.register(UserGroupBinding.class, is -> {
            is.add(IndexSpecs.<UserGroupBinding, String>single("roleRef.name", String.class)
                    .indexFunc(binding -> Optional.ofNullable(binding.getRoleRef())
                            .map(UserGroupBinding.RoleRef::getName)
                            .orElse(null)));
            is.add(IndexSpecs.<UserGroupBinding, String>multi("subjects", String.class)
                    .indexFunc(binding -> Optional.ofNullable(binding.getSubjects())
                            .stream()
                            .flatMap(java.util.Collection::stream)
                            .map(UserGroupBinding.Subject::toString)
                            .collect(Collectors.toSet())));
        });

        schemeManager.register(User.class, is -> {
            is.add(IndexSpecs.<User, String>single("spec.displayName", String.class)
                    .indexFunc(user -> user.getSpec().getDisplayName()));
            is.add(IndexSpecs.<User, Boolean>single("spec.emailVerified", Boolean.class)
                    .indexFunc(user -> user.getSpec().isEmailVerified()));
            is.add(IndexSpecs.<User, String>single("spec.email", String.class)
                    .indexFunc(user -> Optional.ofNullable(user.getSpec().getEmail())
                            .map(String::toLowerCase)
                            .orElse(null)));
            is.add(IndexSpecs.<User, Boolean>single("spec.disabled", Boolean.class)
                    .indexFunc(user -> requireNonNullElse(user.getSpec().getDisabled(), Boolean.FALSE))
                    .nullable(false));
        });

        schemeManager.register(UserConnection.class, is -> {
            is.add(IndexSpecs.<UserConnection, String>single("spec.username", String.class)
                    .indexFunc(connection -> connection.getSpec().getUsername()));
            is.add(IndexSpecs.<UserConnection, String>single("spec.registrationId", String.class)
                    .indexFunc(connection -> connection.getSpec().getRegistrationId()));
            is.add(IndexSpecs.<UserConnection, String>single("spec.providerUserId", String.class)
                    .indexFunc(connection -> connection.getSpec().getProviderUserId()));
        });

        schemeManager.register(Webhook.class, is -> is.add(
                IndexSpecs.<Webhook, Boolean>single("spec.enabled", Boolean.class)
                        .indexFunc(webhook -> webhook.getSpec().isEnabled())));

        schemeManager.register(RiskRule.class, is -> is.add(
                IndexSpecs.<RiskRule, String>single("spec.scene", String.class)
                        .indexFunc(rule -> rule.getSpec().getScene())));

        schemeManager.register(TempRoleBinding.class, is -> {
            is.add(IndexSpecs.<TempRoleBinding, String>single("spec.username", String.class)
                    .indexFunc(binding -> binding.getSpec().getUsername()));
            is.add(IndexSpecs.<TempRoleBinding, String>single("spec.status", String.class)
                    .indexFunc(binding -> binding.getSpec().getStatus()));
            is.add(IndexSpecs.<TempRoleBinding, Instant>single("spec.expiresAt", Instant.class)
                    .indexFunc(binding -> binding.getSpec().getExpiresAt()));
        });

        schemeManager.register(Device.class, is -> is.add(
                IndexSpecs.<Device, String>single("spec.principalName", String.class)
                        .indexFunc(device -> device.getSpec().getPrincipalName())));

        schemeManager.register(RememberMeToken.class, is -> {
            is.add(IndexSpecs.<RememberMeToken, String>single("spec.username", String.class)
                    .indexFunc(token -> token.getSpec().getUsername()));
            is.add(IndexSpecs.<RememberMeToken, String>single("spec.series", String.class)
                    .indexFunc(token -> token.getSpec().getSeries()));
            is.add(IndexSpecs.<RememberMeToken, Instant>single("spec.lastUsed", Instant.class)
                    .indexFunc(token -> token.getSpec().getLastUsed()));
        });

        schemeManager.register(PersonalAccessToken.class, is -> is.add(
                IndexSpecs.<PersonalAccessToken, String>single("spec.username", String.class)
                        .indexFunc(token -> token.getSpec().getUsername())));

        schemeManager.register(AuthProvider.class);
        schemeManager.register(Setting.class);
        schemeManager.register(AnnotationSetting.class, is -> is.add(
                IndexSpecs.<AnnotationSetting, String>single("spec.targetRef", String.class)
                        .indexFunc(annotationSetting -> Optional.ofNullable(annotationSetting.getSpec())
                                .map(AnnotationSetting.AnnotationSettingSpec::getTargetRef)
                                .map(ref -> ref.group() + "/" + ref.kind())
                                .orElse(null))));
        schemeManager.register(ConfigMap.class);
        schemeManager.register(Secret.class);

        schemeManager.register(StoragePolicy.class, is -> {
            is.add(IndexSpecs.<StoragePolicy, StoragePolicyType>single("spec.type", StoragePolicyType.class)
                    .indexFunc(policy -> policy.getSpec().getType()));
            is.add(IndexSpecs.<StoragePolicy, Boolean>single("spec.defaultPolicy", Boolean.class)
                    .indexFunc(policy -> policy.getSpec().isDefaultPolicy()));
        });

        schemeManager.register(StorageFile.class, is -> {
            is.add(IndexSpecs.<StorageFile, String>single("spec.policy", String.class)
                    .indexFunc(file -> file.getSpec().getPolicy()));
            is.add(IndexSpecs.<StorageFile, String>single("spec.owner", String.class)
                    .indexFunc(file -> file.getSpec().getOwner()));
            is.add(IndexSpecs.<StorageFile, String>single("spec.module", String.class)
                    .indexFunc(file -> file.getSpec().getModule()));
            is.add(IndexSpecs.<StorageFile, StorageFileState>single("spec.state", StorageFileState.class)
                    .indexFunc(file -> file.getSpec().getState()));
            is.add(IndexSpecs.<StorageFile, String>single("spec.extension", String.class)
                    .indexFunc(file -> file.getSpec().getExtension()));
            is.add(IndexSpecs.<StorageFile, Instant>single("spec.trashExpiresAt", Instant.class)
                    .indexFunc(file -> file.getSpec().getTrashExpiresAt()));
            is.add(IndexSpecs.<StorageFile, Instant>single("spec.uploadExpiresAt", Instant.class)
                    .indexFunc(file -> file.getSpec().getUploadExpiresAt()));
        });
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return InitializationPhase.SCHEME.getPhase();
    }
}
