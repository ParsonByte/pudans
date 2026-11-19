package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.removeFinalizers;

import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderAccountReconciler implements Reconciler<Reconciler.Request> {

    private static final String FINALIZER = "ai-provider-account-protection";

    private final ExtensionClient client;

    private final AiProviderSecretCipher secretCipher;

    @Override
    public Result reconcile(Request request) {
        var optional = client.fetch(AiProviderAccount.class, request.name());
        if (optional.isEmpty()) {
            return Result.doNotRetry();
        }
        var account = optional.get();
        if (isDeleted(account)) {
            removeFinalizers(account.getMetadata(), Set.of(FINALIZER));
            client.update(account);
            log.info("供应商账号 [{}] 已删除", request.name());
            return Result.doNotRetry();
        }
        var changed = addFinalizers(account.getMetadata(), Set.of(FINALIZER));
        if (encryptApiKey(account)) {
            changed = true;
        }
        if (changed) {
            client.update(account);
        }
        return Result.doNotRetry();
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new AiProviderAccount()).build();
    }

    private boolean encryptApiKey(AiProviderAccount account) {
        var spec = account.getSpec();
        if (spec == null) {
            spec = new AiProviderAccount.Spec();
            account.setSpec(spec);
            return true;
        }
        var apiKey = spec.getApiKey();
        if (!StringUtils.hasText(apiKey) || secretCipher.isEncrypted(apiKey)) {
            return false;
        }
        spec.setApiKey(secretCipher.encrypt(apiKey));
        return true;
    }
}
