package cn.tolim.pudans.core.storage.reconciler;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.removeFinalizers;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

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
import cn.tolim.pudans.contract.storage.resource.StoragePolicy;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
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

    private final FileRecords fileService;

    private final StorageDriverResolver driverResolver;

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

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new StoragePolicy()).build();
    }

    private void handleDelete(StoragePolicy policy) {
        var name = policy.getMetadata().getName();
        var builtin = Boolean.parseBoolean(
                MetadataUtil.nullSafeLabels(policy).get(BuiltinEntities.BUILTIN_LABEL));
        var referenced = Boolean.TRUE.equals(
                fileService.existsByPolicy(name).blockOptional(LOOKUP_TIMEOUT).orElse(false));
        if (builtin || policy.getSpec().isDefaultPolicy() || referenced) {
            policy.getMetadata().setDeletionTimestamp(null);
            var status = Optional.ofNullable(policy.getStatus()).orElseGet(StoragePolicy.Status::new);
            status.setMessage(referenced
                    ? "存储策略仍被文件引用，无法删除。"
                    : "默认或内置存储策略不可删除。");
            policy.setStatus(status);
            client.update(policy);
            return;
        }
        driverResolver.evict(name);
        removeFinalizers(policy.getMetadata(), Set.of(FINALIZER));
        client.update(policy);
        log.info("存储策略 [{}] 已删除", name);
    }

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

    private boolean needsEncryption(String value) {
        return StringUtils.hasText(value) && !secretCipher.isEncrypted(value);
    }

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

    private static Map<String, Boolean> credentialConfigured(StoragePolicy.Spec spec) {
        var credentials = spec.getCredentials();
        var configured = new LinkedHashMap<String, Boolean>();
        configured.put("accessKey", credentials != null && StringUtils.hasText(credentials.getAccessKey()));
        configured.put("secretKey", credentials != null && StringUtils.hasText(credentials.getSecretKey()));
        configured.put("token", credentials != null && StringUtils.hasText(credentials.getToken()));
        configured.put("clientSecret", credentials != null && StringUtils.hasText(credentials.getClientSecret()));
        return configured;
    }

    private static boolean sameStatus(StoragePolicy.Status left, StoragePolicy.Status right) {
        if (left == null) {
            return false;
        }
        return left.isReady() == right.isReady()
                && Objects.equals(left.getMessage(), right.getMessage())
                && Objects.equals(left.getLastTestedAt(), right.getLastTestedAt())
                && Objects.equals(left.getCredentialConfigured(), right.getCredentialConfigured());
    }

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
