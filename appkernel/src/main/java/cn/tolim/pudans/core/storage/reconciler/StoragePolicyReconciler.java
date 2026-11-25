package cn.tolim.pudans.core.storage.reconciler;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.removeFinalizers;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.StoragePolicyValidator;
import cn.tolim.pudans.core.storage.StoragePolicySecretCipher;
import cn.tolim.pudans.core.storage.FileRecords;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoragePolicyReconciler implements Reconciler<Reconciler.Request> {

    private static final String FINALIZER = "storage-policy-protection";

    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(10);

    private final ExtensionClient client;

    private final StoragePolicyValidator validator;

    private final StoragePolicySecretCipher secretCipher;

    private final FileRecords fileRecords;

    private final StorageDriverResolver driverResolver;

    // 补齐终结器、加密凭据并同步策略状态。
    @Override
    public Result reconcile(Request request) {
        var optional = client.fetch(StoragePolicy.class, request.name());
        if (optional.isEmpty()) {
            return Result.doNotRetry();
        }
        var policy = optional.get();
        if (isDeleted(policy)) {
            handleDelete(policy);
            return Result.doNotRetry();
        }
        var changed = false;
        if (addFinalizers(policy.getMetadata(), Set.of(FINALIZER))) {
            changed = true;
        }
        if (encryptCredentials(policy)) {
            changed = true;
        }
        var validation = validator.validatePolicy(policy);
        if (!validation.valid() && policy.getSpec().isDefaultPolicy()) {
            policy.getSpec().setDefaultPolicy(false);
            changed = true;
        }
        if (updateStatus(policy, validation)) {
            changed = true;
        }
        if (changed) {
            client.update(policy);
        }
        if (validation.valid() && policy.getSpec().isDefaultPolicy()) {
            clearOtherDefaults(policy);
        }
        return Result.doNotRetry();
    }

    // 声明该协调器负责存储策略资源。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new StoragePolicy()).build();
    }

    // 阻止删除仍被引用或受保护的策略。
    private void handleDelete(StoragePolicy policy) {
        var name = policy.getMetadata().getName();
        var builtin = Boolean.parseBoolean(
                MetadataUtil.nullSafeLabels(policy).get(BuiltinEntities.BUILTIN_LABEL));
        var referencedByFiles =
                Boolean.TRUE.equals(
                        fileRecords.existsByPolicy(name).blockOptional(LOOKUP_TIMEOUT).orElse(false));
        var referencedByLive = referencedByLiveResources(name);
        var referencedByRoutes = referencedByStorageRoutes(name);
        var referenced = referencedByFiles || referencedByLive || referencedByRoutes;
        if (builtin || policy.getSpec().isDefaultPolicy() || referenced) {
            policy.getMetadata().setDeletionTimestamp(null);
            var status = Optional.ofNullable(policy.getStatus()).orElseGet(StoragePolicy.Status::new);
            if (referencedByRoutes) {
                status.setMessage("存储策略仍被存储路由引用，无法删除。");
            } else {
                status.setMessage(referenced
                        ? "存储策略仍被文件或直播引用，无法删除。"
                        : "默认或内置存储策略不可删除。");
            }
            policy.setStatus(status);
            client.update(policy);
            return;
        }
        driverResolver.evict(name);
        removeFinalizers(policy.getMetadata(), Set.of(FINALIZER));
        client.update(policy);
        log.info("存储策略 [{}] 已删除", name);
    }

    // 判断直播源或录像是否仍引用该策略。
    private boolean referencedByLiveResources(String name) {
        var sourceOptions = ListOptions.builder()
                .andQuery(equal("spec.storagePolicy", name))
                .build();
        if (!client.listAll(LiveSource.class, sourceOptions, defaultSort()).isEmpty()) {
            return true;
        }
        var recordingOptions = ListOptions.builder()
                .andQuery(equal("spec.storagePolicy", name))
                .build();
        return !client.listAll(LiveRecording.class, recordingOptions, defaultSort()).isEmpty();
    }

    // 判断存储路由是否仍绑定该策略，包括停用路由。
    private boolean referencedByStorageRoutes(String name) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.policyName", name))
                .build();
        return !client.listAll(StorageRoute.class, options, defaultSort()).isEmpty();
    }

    // 加密尚未加密的凭据并报告是否变更。
    private boolean encryptCredentials(StoragePolicy policy) {
        var spec = policy.getSpec();
        var credentials = spec.getCredentials();
        if (credentials == null) {
            credentials = new StoragePolicy.Credentials();
            spec.setCredentials(credentials);
            return true;
        }
        var changed = false;
        if (needsEncryption(credentials.getAccessKey())) {
            credentials.setAccessKey(secretCipher.encrypt(credentials.getAccessKey()));
            changed = true;
        }
        if (needsEncryption(credentials.getSecretKey())) {
            credentials.setSecretKey(secretCipher.encrypt(credentials.getSecretKey()));
            changed = true;
        }
        if (needsEncryption(credentials.getToken())) {
            credentials.setToken(secretCipher.encrypt(credentials.getToken()));
            changed = true;
        }
        if (needsEncryption(credentials.getClientSecret())) {
            credentials.setClientSecret(secretCipher.encrypt(credentials.getClientSecret()));
            changed = true;
        }
        return changed;
    }

    // 判断凭据值是否需要加密。
    private boolean needsEncryption(String value) {
        return StringUtils.hasText(value) && !secretCipher.isEncrypted(value);
    }

    // 根据校验结果刷新状态，未变化时跳过更新。
    private boolean updateStatus(StoragePolicy policy, StoragePolicyValidator.ValidationResult validation) {
        var current = policy.getStatus();
        var status = new StoragePolicy.Status();
        status.setReady(validation.valid());
        status.setMessage(validation.valid() ? null : String.join(" ", validation.errors()));
        status.setLastTestedAt(current == null ? null : current.getLastTestedAt());
        status.setCredentialConfigured(credentialConfigured(policy.getSpec()));
        if (sameStatus(current, status)) {
            return false;
        }
        policy.setStatus(status);
        return true;
    }

    // 汇总各类凭据是否已配置。
    private static Map<String, Boolean> credentialConfigured(StoragePolicy.Spec spec) {
        var credentials = spec.getCredentials();
        var configured = new LinkedHashMap<String, Boolean>();
        configured.put("accessKey", credentials != null && StringUtils.hasText(credentials.getAccessKey()));
        configured.put("secretKey", credentials != null && StringUtils.hasText(credentials.getSecretKey()));
        configured.put("token", credentials != null && StringUtils.hasText(credentials.getToken()));
        configured.put("clientSecret", credentials != null && StringUtils.hasText(credentials.getClientSecret()));
        return configured;
    }

    // 比较两份状态，决定是否需要写回。
    private static boolean sameStatus(StoragePolicy.Status left, StoragePolicy.Status right) {
        if (left == null) {
            return false;
        }
        return left.isReady() == right.isReady()
                && Objects.equals(left.getMessage(), right.getMessage())
                && Objects.equals(left.getLastTestedAt(), right.getLastTestedAt())
                && Objects.equals(left.getCredentialConfigured(), right.getCredentialConfigured());
    }

    // 取消其他策略的默认标记，保证默认唯一。
    private void clearOtherDefaults(StoragePolicy policy) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.defaultPolicy", true))
                .build();
        for (var other : client.listAll(StoragePolicy.class, options, defaultSort())) {
            if (!Objects.equals(other.getMetadata().getName(), policy.getMetadata().getName())) {
                other.getSpec().setDefaultPolicy(false);
                client.update(other);
            }
        }
    }
}
