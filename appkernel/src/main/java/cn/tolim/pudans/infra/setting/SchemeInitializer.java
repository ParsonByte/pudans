package cn.tolim.pudans.infra.setting;

import static cn.tolim.pudans.contract.rbac.Role.ROLE_AGGREGATE_LABEL_PREFIX;
import static java.util.Objects.requireNonNullElse;

import cn.tolim.pudans.setting.contract.AnnotationSetting;
import cn.tolim.pudans.security.contract.AuthProvider;
import cn.tolim.pudans.security.contract.Device;
import cn.tolim.pudans.security.contract.RememberMeToken;
import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.setting.contract.Setting;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.RoleBinding;
import cn.tolim.pudans.contract.rbac.TempRoleBinding;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.SchemeManager;
import cn.tolim.pudans.extension.contract.Secret;
import cn.tolim.pudans.extension.contract.index.IndexSpecs;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
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

    // 注入索引方案管理器，用于注册资源索引。
    public SchemeInitializer(SchemeManager schemeManager) {
        this.schemeManager = schemeManager;
    }

    // 为各扩展资源注册查询与排序所需的索引。
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
            is.add(IndexSpecs.<User, String>single("spec.uid", String.class)
                    .indexFunc(user -> user.getSpec().getUid()));
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

        schemeManager.register(LiveSource.class, is -> {
            is.add(IndexSpecs.<LiveSource, Boolean>single("spec.enabled", Boolean.class)
                    .indexFunc(source -> source.getSpec().isEnabled()));
            is.add(IndexSpecs.<LiveSource, String>single("spec.roomId", String.class)
                    .indexFunc(source -> source.getSpec().getRoomId()));
            is.add(IndexSpecs.<LiveSource, LiveSourceState>single("status.state", LiveSourceState.class)
                    .indexFunc(source -> source.getStatus().getState()));
            is.add(IndexSpecs.<LiveSource, Boolean>single("status.live", Boolean.class)
                    .indexFunc(source -> source.getStatus().isLive()));
            is.add(IndexSpecs.<LiveSource, Boolean>single("status.rateLimited", Boolean.class)
                    .indexFunc(source -> source.getStatus().isRateLimited()));
            is.add(IndexSpecs.<LiveSource, Instant>single("status.nextCheckAt", Instant.class)
                    .indexFunc(source -> source.getStatus().getNextCheckAt()));
        });

        schemeManager.register(LiveRecording.class, is -> {
            is.add(IndexSpecs.<LiveRecording, String>single("spec.sourceName", String.class)
                    .indexFunc(recording -> recording.getSpec().getSourceName()));
            is.add(IndexSpecs.<LiveRecording, LiveRecordingState>single(
                            "status.state", LiveRecordingState.class)
                    .indexFunc(recording -> recording.getStatus().getState()));
            is.add(IndexSpecs.<LiveRecording, Instant>single("spec.startedAt", Instant.class)
                    .indexFunc(recording -> recording.getSpec().getStartedAt()));
            is.add(IndexSpecs.<LiveRecording, String>single("status.ownerInstance", String.class)
                    .indexFunc(recording -> recording.getStatus().getOwnerInstance()));
            is.add(IndexSpecs.<LiveRecording, Instant>single("status.heartbeatAt", Instant.class)
                    .indexFunc(recording -> recording.getStatus().getHeartbeatAt()));
        });
    }

    // 标记索引注册完成，防止重复注册。
    @Override
    public void stop() {
        running = false;
    }

    // 返回索引方案是否已注册。
    @Override
    public boolean isRunning() {
        return running;
    }

    // 在扩展资源加载前完成索引注册。
    @Override
    public int getPhase() {
        return InitializationPhase.SCHEME.getPhase();
    }
}
