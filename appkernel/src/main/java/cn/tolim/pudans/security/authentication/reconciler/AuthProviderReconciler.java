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

    @Override
    public Result reconcile(Request request) {
        client.fetch(AuthProvider.class, request.name()).ifPresent(this::handlePrivileged);
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AuthProvider()).build();
    }

    private void handlePrivileged(AuthProvider authProvider) {
        if (AuthProviderUtils.isPrivileged(authProvider)) {
            authProviders.enable(authProvider.getMetadata().getName()).block(BLOCKING_TIMEOUT);
        }
    }
}
