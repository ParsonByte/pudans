package cn.tolim.pudans.core.storage.reconciler;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.removeFinalizers;

import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageRouteReconciler implements Reconciler<Reconciler.Request> {

    private static final String FINALIZER = "storage-route-protection";

    private final ExtensionClient client;

    // 校验路由目标并维护删除终结器。
    @Override
    public Result reconcile(Request request) {
        var optional = client.fetch(StorageRoute.class, request.name());
        if (optional.isEmpty()) {
            return Result.doNotRetry();
        }
        var route = optional.get();
        if (isDeleted(route)) {
            handleDelete(route);
            return Result.doNotRetry();
        }
        var error = validationError(route);
        if (error != null) {
            log.warn("存储路由 [{}] 校验失败：{}", request.name(), error);
            return Result.doNotRetry();
        }
        if (addFinalizers(route.getMetadata(), Set.of(FINALIZER))) {
            client.update(route);
        }
        return Result.doNotRetry();
    }

    // 声明该协调器负责存储路由资源。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new StorageRoute()).build();
    }

    // 删除路由只解除引用，不删除任何存储文件。
    private void handleDelete(StorageRoute route) {
        if (removeFinalizers(route.getMetadata(), Set.of(FINALIZER))) {
            client.update(route);
        }
        log.info("存储路由 [{}] 已删除", route.getMetadata().getName());
    }

    // 返回首个结构或引用校验错误，通过时返回空。
    private String validationError(StorageRoute route) {
        var spec = route.getSpec();
        if (spec == null || spec.getPurpose() == null) {
            return "用途不能为空";
        }
        var name = route.getMetadata() == null ? null : route.getMetadata().getName();
        if (!Objects.equals(name, spec.getPurpose().routeName())) {
            return "资源名称必须为 " + spec.getPurpose().routeName();
        }
        if (!StringUtils.hasText(spec.getPolicyName())) {
            return "目标策略不能为空";
        }
        var exists = client.fetch(StoragePolicy.class, spec.getPolicyName()).isPresent();
        return exists ? null : "目标策略不存在：" + spec.getPolicyName();
    }
}
