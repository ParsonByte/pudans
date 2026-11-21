package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.supplier.SupplierWebSocketAccess;
import cn.tolim.pudans.core.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class QianwenProviderAccess implements SupplierWebSocketAccess {

    static final String CODE_DISABLED = "qianwen-provider-disabled";

    static final String CODE_UNSUPPORTED = "qianwen-provider-unsupported";

    static final String CODE_NO_ACCOUNT = "qianwen-no-account";

    private final ReactiveExtensionClient client;

    private final ProviderAccounts providerAccounts;

    @Override
    public Mono<AiProvider> requireProvider(String name) {
        return client.get(AiProvider.class, name).flatMap(provider -> {
            var spec = provider.getSpec();
            if (spec == null || !spec.isEnabled()) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_DISABLED, "供应商不存在或已禁用。"));
            }
            if (!QianwenProtocol.isQianwen(spec.getProtocol())) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        CODE_UNSUPPORTED,
                        "供应商协议不是千问：" + spec.getProtocol()));
            }
            return Mono.just(provider);
        });
    }

    @Override
    public Mono<AiProviderAccount> requireAccount(AiProvider provider) {
        return providerAccounts
                .firstEnabled(provider.getMetadata().getName())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, CODE_NO_ACCOUNT, "供应商没有可用账号。")));
    }
}
