package cn.tolim.pudans.core.webhook.reconciler;

import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.extension.api.ExtensionClient;
import cn.tolim.pudans.extension.api.controller.Controller;
import cn.tolim.pudans.extension.api.controller.ControllerBuilder;
import cn.tolim.pudans.extension.api.controller.Reconciler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.core.webhook.WebhookSecretCipher;

@Component
@RequiredArgsConstructor
public class WebhookReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    private final WebhookSecretCipher secretCipher;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Webhook.class, request.name()).ifPresent(webhook -> {
            var spec = webhook.getSpec();
            if (spec == null || !StringUtils.hasText(spec.getSecret())) {
                return;
            }
            if (secretCipher.isEncrypted(spec.getSecret())) {
                return;
            }
            spec.setSecret(secretCipher.encrypt(spec.getSecret()));
            client.update(webhook);
        });
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Webhook()).build();
    }
}
