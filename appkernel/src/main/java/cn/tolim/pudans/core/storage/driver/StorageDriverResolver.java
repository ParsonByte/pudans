package cn.tolim.pudans.core.storage.driver;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.core.storage.StoragePolicySecretCipher;
import cn.tolim.pudans.core.storage.Policies;
import cn.tolim.pudans.core.storage.StoragePolicyValidator;
import cn.tolim.pudans.core.infra.exception.ApiException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class StorageDriverResolver {

    private final Policies policies;

    private final StorageDriverFactoryRegistry registry;

    private final StoragePolicySecretCipher secretCipher;

    private final StoragePolicyValidator validator;

    private final ConcurrentMap<String, CachedDriver> drivers = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    public StorageDriverResolver(
            Policies policies,
            StorageDriverFactoryRegistry registry,
            StoragePolicySecretCipher secretCipher,
            StoragePolicyValidator validator) {
        this.policies = policies;
        this.registry = registry;
        this.secretCipher = secretCipher;
        this.validator = validator;
    }

    public Mono<StorageDriver> driver(String policyName) {
        return policies.requireReady(policyName).flatMap(this::driver);
    }

    public Mono<StorageDriver> defaultDriver() {
        return policies.defaultPolicy().flatMap(this::driver);
    }

    public Mono<StorageDriver> driver(StoragePolicy policy) {
        return Mono.fromCallable(() -> resolve(policy)).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<StorageTestResult> test(String policyName) {
        return driver(policyName).flatMap(StorageDriver::testConnection);
    }

    public void evict(String policyName) {
        synchronized (lock) {
            var cached = drivers.remove(policyName);
            if (cached != null) {
                closeQuietly(cached.driver());
            }
        }
    }

    private StorageDriver resolve(StoragePolicy policy) {
        var name = policy.getMetadata().getName();
        synchronized (lock) {
            var cached = drivers.get(name);
            if (cached != null && cached.spec().equals(policy.getSpec())) {
                return cached.driver();
            }
            var factory = registry.factory(policy.getSpec().getType())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "storage-policy-type-unsupported",
                            "不支持的存储类型：" + policy.getSpec().getType()));
            // 建立连接前校验服务地址解析结果，避免存储策略指向本机或内网。
            validator.verifyEndpointReachable(policy.getSpec());
            var driver = factory.create(new ResolvedStoragePolicy(policy, decrypt(policy.getSpec().getCredentials())));
            if (cached != null) {
                closeQuietly(cached.driver());
            }
            drivers.put(name, new CachedDriver(policy.getSpec(), driver));
            return driver;
        }
    }

    private StoragePolicy.Credentials decrypt(StoragePolicy.Credentials stored) {
        var credentials = new StoragePolicy.Credentials();
        if (stored == null) {
            return credentials;
        }
        credentials.setAccessKey(decryptValue(stored.getAccessKey()));
        credentials.setSecretKey(decryptValue(stored.getSecretKey()));
        credentials.setToken(decryptValue(stored.getToken()));
        credentials.setClientSecret(decryptValue(stored.getClientSecret()));
        return credentials;
    }
    private String decryptValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return secretCipher.decrypt(value);
    }

    private static void closeQuietly(StorageDriver driver) {
        try {
            driver.close();
        } catch (Exception e) {
            log.warn("关闭存储驱动失败", e);
        }
    }

    @PreDestroy
    void closeAll() {
        synchronized (lock) {
            drivers.values().forEach(cached -> closeQuietly(cached.driver()));
            drivers.clear();
        }
    }

    private record CachedDriver(StoragePolicy.Spec spec, StorageDriver driver) {}
}
