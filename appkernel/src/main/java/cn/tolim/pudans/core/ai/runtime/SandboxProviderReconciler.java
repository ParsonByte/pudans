package cn.tolim.pudans.core.ai.runtime;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.removeFinalizers;

import cn.tolim.pudans.contract.ai.AiSandboxProvider;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.core.ai.provider.AiProviderSecretCipher;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxProviderReconciler implements Reconciler<Reconciler.Request> {

    private static final String FINALIZER = "ai-sandbox-provider-protection";

    private final ExtensionClient client;

    private final AiProviderSecretCipher secretCipher;

    @Override
    public Result reconcile(Request request) {
        var optional = client.fetch(AiSandboxProvider.class, request.name());
        if (optional.isEmpty()) {
            return Result.doNotRetry();
        }
        var provider = optional.get();
        if (isDeleted(provider)) {
            removeFinalizers(provider.getMetadata(), Set.of(FINALIZER));
            client.update(provider);
            log.info("沙箱提供方 [{}] 已删除", request.name());
            return Result.doNotRetry();
        }
        var changed = addFinalizers(provider.getMetadata(), Set.of(FINALIZER));
        if (encryptSecret(provider)) {
            changed = true;
        }
        if (changed) {
            client.update(provider);
        }
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AiSandboxProvider()).build();
    }

    private boolean encryptSecret(AiSandboxProvider provider) {
        var spec = provider.getSpec();
        if (spec == null) {
            return false;
        }
        var secret = spec.getAccessKeySecret();
        if (!StringUtils.hasText(secret) || secretCipher.isEncrypted(secret)) {
            return false;
        }
        spec.setAccessKeySecret(secretCipher.encrypt(secret));
        return true;
    }
}