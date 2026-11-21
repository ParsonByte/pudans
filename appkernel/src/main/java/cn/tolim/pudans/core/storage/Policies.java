package cn.tolim.pudans.core.storage;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.core.infra.exception.ApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Policies {

    private final ReactiveExtensionClient client;

    public Mono<StoragePolicy> get(String name) {
        return client.get(StoragePolicy.class, name);
    }

    public Mono<StoragePolicy> update(StoragePolicy policy) {
        return client.update(policy);
    }

    public Flux<StoragePolicy> list(StoragePolicyType type) {
        var options = ListOptions.builder().andQuery(equal("spec.type", type)).build();
        return client.listAll(StoragePolicy.class, options, defaultSort());
    }

    public Mono<StoragePolicy> defaultPolicy() {
        var options = ListOptions.builder()
                .andQuery(equal("spec.defaultPolicy", true))
                .build();
        return client.listAll(StoragePolicy.class, options, defaultSort())
                .filter(policy -> policy.getStatus() != null && policy.getStatus().isReady())
                .next()
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "storage-policy-default-missing",
                        "系统未配置可用的默认存储策略。")));
    }

    public Mono<StoragePolicy> requireReady(String name) {
        return get(name).flatMap(policy -> {
            if (policy.getStatus() != null && policy.getStatus().isReady()) {
                return Mono.just(policy);
            }
            var message = policy.getStatus() == null ? "策略尚未就绪。" : policy.getStatus().getMessage();
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "storage-policy-not-ready", "存储策略不可用：" + message));
        });
    }

    public Mono<StoragePolicy> recordTestResult(String name, StorageTestResult result) {
        return get(name).flatMap(policy -> {
            var status = Optional.ofNullable(policy.getStatus()).orElseGet(StoragePolicy.Status::new);
            status.setLastTestedAt(result.testedAt());
            status.setMessage(result.message());
            policy.setStatus(status);
            return client.update(policy);
        });
    }
}
