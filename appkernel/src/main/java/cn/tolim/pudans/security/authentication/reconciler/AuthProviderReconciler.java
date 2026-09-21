package cn.tolim.pudans.security.authentication.reconciler;

import cn.tolim.pudans.contract.core.extension.AuthProvider;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import cn.tolim.pudans.security.authentication.AuthProviderService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class AuthProviderReconciler implements Reconciler<Reconciler.Request> {
    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;
    private final ExtensionClient client;
    private final AuthProviderService authProviderService;

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
        if (privileged(authProvider)) {
            authProviderService.enable(authProvider.getMetadata().getName()).block(BLOCKING_TIMEOUT);
        }
    }

    private boolean privileged(AuthProvider authProvider) {
        return BooleanUtils.TRUE.equals(
                MetadataUtil.nullSafeLabels(authProvider).get(AuthProvider.PRIVILEGED_LABEL));
    }
}
