package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class DeepSeekProviderAccess {

    static final String CODE_DISABLED = "ai-deepseek-provider-disabled";

    static final String CODE_UNSUPPORTED = "ai-deepseek-provider-unsupported";

    static final String CODE_NO_ACCOUNT = "ai-deepseek-no-account";

    static final String CODE_MODEL_INVALID = "ai-deepseek-model-invalid";

    static final String CODE_ACCOUNT_INVALID = "ai-deepseek-account-invalid";

    private final ReactiveExtensionClient client;

    private final ProviderAccounts providerAccounts;

    public Mono<AiProvider> requireProvider(String name) {
        return client.get(AiProvider.class, name).flatMap(this::checkProvider);
    }

    public Mono<AiProvider> requireProviderOf(AiProviderAccount account) {
        if (account == null || account.getSpec() == null
                || !StringUtils.hasText(account.getSpec().getProvider())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_ACCOUNT_INVALID, "账号未绑定供应商。"));
        }
        return requireProvider(account.getSpec().getProvider());
    }

    public Mono<AiProviderAccount> requireAccount(AiProvider provider) {
        return providerAccounts
                .firstEnabled(provider.getMetadata().getName())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, CODE_NO_ACCOUNT, "供应商没有可用账号。")));
    }

    public Mono<AiModel> requireChatModel(String modelName, AiProvider provider) {
        if (!StringUtils.hasText(modelName)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_MODEL_INVALID, "model 不能为空。"));
        }
        return client.get(AiModel.class, modelName).flatMap(model -> {
            var spec = model.getSpec();
            if (spec == null
                    || !provider.getMetadata().getName().equals(spec.getProvider())
                    || spec.getModelType() != AiModel.ModelType.CHAT) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        CODE_MODEL_INVALID,
                        "模型不属于该供应商或不是对话模型。"));
            }
            return Mono.just(model);
        });
    }

    private Mono<AiProvider> checkProvider(AiProvider provider) {
        var spec = provider.getSpec();
        if (spec == null || !spec.isEnabled()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_DISABLED, "供应商不存在或已禁用。"));
        }
        if (!DeepSeekProtocol.isDeepSeek(spec.getProtocol())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    CODE_UNSUPPORTED,
                    "供应商协议不是 DeepSeek：" + spec.getProtocol()));
        }
        return Mono.just(provider);
    }
}
