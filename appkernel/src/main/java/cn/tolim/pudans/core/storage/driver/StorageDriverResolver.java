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
import cn.tolim.pudans.infra.exception.ApiException;
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

    // 注入策略、工厂、密钥与校验依赖。
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

    // 读取策略并返回其就绪的驱动实例。
    public Mono<StorageDriver> driver(String policyName) {
        return policies.requireReady(policyName).flatMap(this::driver);
    }

    // 返回默认策略对应的驱动实例。
    public Mono<StorageDriver> defaultDriver() {
        return policies.defaultPolicy().flatMap(this::driver);
    }

    // 在阻塞线程池中解析或创建策略驱动。
    public Mono<StorageDriver> driver(StoragePolicy policy) {
        return Mono.fromCallable(() -> resolve(policy)).subscribeOn(Schedulers.boundedElastic());
    }

    // 通过驱动测试指定策略的连通性。
    public Mono<StorageTestResult> test(String policyName) {
        return driver(policyName).flatMap(StorageDriver::testConnection);
    }

    // 移除并关闭指定策略的缓存驱动。
    public void evict(String policyName) {
        synchronized (lock) {
            var cached = drivers.remove(policyName);
            if (cached != null) {
                closeQuietly(cached.driver());
            }
        }
    }

    // 复用配置未变的驱动，变化时重建并关闭旧实例。
    private StorageDriver resolve(StoragePolicy policy) {
        var name = policy.getMetadata().getName();
        synchronized (lock) {
            // 每次解析都重新校验 DNS，避免缓存驱动期间发生重绑定。
            validator.verifyEndpointReachable(policy.getSpec());
            var cached = drivers.get(name);
            if (cached != null && cached.spec().equals(policy.getSpec())) {
                return cached.driver();
            }
            var factory = registry.factory(policy.getSpec().getType())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "storage-policy-type-unsupported",
                            "不支持的存储类型：" + policy.getSpec().getType()));
            var driver = factory.create(new ResolvedStoragePolicy(policy, decrypt(policy.getSpec().getCredentials())));
            if (cached != null) {
                closeQuietly(cached.driver());
            }
            drivers.put(name, new CachedDriver(policy.getSpec(), driver));
            return driver;
        }
    }

    // 复制并解密凭据，避免修改存储中的密文。
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
    // 解密单个凭据值，空值保持原样。
    private String decryptValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return secretCipher.decrypt(value);
    }

    // 关闭驱动并忽略关闭异常。
    private static void closeQuietly(StorageDriver driver) {
        try {
            driver.close();
        } catch (Exception e) {
            log.warn("关闭存储驱动失败", e);
        }
    }

    // 应用关闭时释放全部缓存驱动。
    @PreDestroy
    void closeAll() {
        synchronized (lock) {
            drivers.values().forEach(cached -> closeQuietly(cached.driver()));
            drivers.clear();
        }
    }

    private record CachedDriver(StoragePolicy.Spec spec, StorageDriver driver) {}
}
