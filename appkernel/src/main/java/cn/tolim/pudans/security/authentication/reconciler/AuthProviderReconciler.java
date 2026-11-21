package cn.tolim.pudans.security.authentication.reconciler;

import cn.tolim.pudans.security.contract.AuthProvider;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import cn.tolim.pudans.security.authentication.AuthProviders;
import cn.tolim.pudans.security.authentication.AuthProviderUtils;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class AuthProviderReconciler implements Reconciler<Reconciler.Request> {
    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;
    private final ExtensionClient client;
    private final AuthProviders authProviders;

    // 拉取提供方配置，特权提供方直接启用且无需重试。
    @Override
    public Result reconcile(Request request) {
        client.fetch(AuthProvider.class, request.name()).ifPresent(this::handlePrivileged);
        return Result.doNotRetry();
    }

    // 声明本协调器负责的扩展类型。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AuthProvider()).build();
    }

    // 仅对标记为特权的提供方执行启用操作。
    private void handlePrivileged(AuthProvider authProvider) {
        if (AuthProviderUtils.isPrivileged(authProvider)) {
            authProviders.enable(authProvider.getMetadata().getName()).block(BLOCKING_TIMEOUT);
        }
    }
}
