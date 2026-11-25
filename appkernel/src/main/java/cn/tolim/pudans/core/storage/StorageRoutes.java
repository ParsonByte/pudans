package cn.tolim.pudans.core.storage;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class StorageRoutes {

    private final ReactiveExtensionClient client;

    private final Policies policies;

    // 列出全部存储路由，供管理端维护用途绑定。
    public Flux<StorageRoute> list() {
        return client.listAll(
                StorageRoute.class,
                ListOptions.builder().build(),
                defaultSort());
    }

    // 按用途读取已配置的路由。
    public Mono<StorageRoute> get(StorageRoute.Purpose purpose) {
        return find(purpose)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        "storage-route-not-found",
                        "存储路由尚未配置：" + purpose.getCode() + "。")));
    }

    // 按用途创建或替换路由，并校验目标策略存在。
    public Mono<StorageRoute> put(StorageRoute.Purpose purpose, StorageRoute.Spec requested) {
        return validate(purpose, requested)
                .flatMap(spec -> find(purpose)
                        .flatMap(route -> {
                            apply(route, purpose, spec);
                            return client.update(route);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            var route = new StorageRoute();
                            var metadata = new Metadata();
                            metadata.setName(purpose.routeName());
                            route.setMetadata(metadata);
                            apply(route, purpose, spec);
                            return client.create(route);
                        })));
    }

    // 删除路由绑定，已有文件仍保留在原策略。
    public Mono<StorageRoute> delete(StorageRoute.Purpose purpose) {
        return get(purpose).flatMap(client::delete);
    }

    // 按用途解析实际写入策略，并按用途执行回退规则。
    public Mono<StoragePolicy> resolve(StorageRoute.Purpose purpose) {
        return find(purpose)
                .flatMap(route -> resolveRoute(purpose, route))
                .switchIfEmpty(Mono.defer(() -> fallback(purpose)));
    }

    // 读取指定用途的路由，缺失时返回空。
    private Mono<StorageRoute> find(StorageRoute.Purpose purpose) {
        return client.fetch(StorageRoute.class, purpose.routeName());
    }

    // 校验请求用途、策略名称和目标策略是否存在。
    private Mono<StorageRoute.Spec> validate(StorageRoute.Purpose purpose, StorageRoute.Spec requested) {
        if (requested == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "storage-route-spec-required",
                    "存储路由配置不能为空。"));
        }
        if (requested.getPurpose() != null && requested.getPurpose() != purpose) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "storage-route-purpose-mismatch",
                    "路由用途必须与请求路径一致。"));
        }
        if (!StringUtils.hasText(requested.getPolicyName())) {
            return Mono.error(policyMissing(requested.getPolicyName()));
        }
        return client.fetch(StoragePolicy.class, requested.getPolicyName())
                .switchIfEmpty(Mono.error(policyMissing(requested.getPolicyName())))
                .thenReturn(requested);
    }

    // 将请求字段写入资源，不从请求体接受名称或元数据。
    private static void apply(
            StorageRoute route, StorageRoute.Purpose purpose, StorageRoute.Spec requested) {
        route.getSpec().setPurpose(purpose);
        route.getSpec().setPolicyName(requested.getPolicyName());
        route.getSpec().setEnabled(requested.isEnabled());
        route.getSpec().setDescription(requested.getDescription());
    }

    // 解析已存在且启用的路由，禁止静默替换不可用策略。
    private Mono<StoragePolicy> resolveRoute(
            StorageRoute.Purpose purpose, StorageRoute route) {
        var spec = route.getSpec();
        var name = route.getMetadata() == null ? null : route.getMetadata().getName();
        if (spec == null
                || spec.getPurpose() != purpose
                || !Objects.equals(name, purpose.routeName())) {
            return Mono.error(new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "storage-route-invalid",
                    "存储路由配置无效：" + purpose.getCode() + "。"));
        }
        if (!spec.isEnabled()) {
            return fallback(purpose);
        }
        if (!StringUtils.hasText(spec.getPolicyName())) {
            return Mono.error(policyMissing(spec.getPolicyName()));
        }
        return client.fetch(StoragePolicy.class, spec.getPolicyName())
                .switchIfEmpty(Mono.error(policyMissing(spec.getPolicyName())))
                .flatMap(policy -> {
                    if (policy.getStatus() != null && policy.getStatus().isReady()) {
                        return Mono.just(policy);
                    }
                    var detail = policy.getStatus() == null ? null : policy.getStatus().getMessage();
                    var message = StringUtils.hasText(detail)
                            ? "存储路由目标策略不可用：" + detail
                            : "存储路由目标策略不可用：" + spec.getPolicyName() + "。";
                    return Mono.error(new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "storage-route-policy-not-ready",
                            message));
                });
    }

    // 仅生成结果允许回退默认策略，沙箱快照必须显式配置。
    private Mono<StoragePolicy> fallback(StorageRoute.Purpose purpose) {
        if (purpose.fallbackToDefaultPolicy()) {
            return policies.defaultPolicy();
        }
        return Mono.error(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "storage-route-not-configured",
                "存储路由尚未配置：" + purpose.getCode() + "。"));
    }

    // 构造目标策略缺失的稳定错误。
    private static ApiException policyMissing(String policyName) {
        var name = StringUtils.hasText(policyName) ? policyName : "<empty>";
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "storage-route-policy-missing",
                "存储路由目标策略不存在：" + name + "。");
    }
}
