package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ProviderAccounts {

    private final ReactiveExtensionClient client;

    private final AiProviderSecretCipher secretCipher;

    public Mono<AiProviderAccount> get(String name) {
        return client.get(AiProviderAccount.class, name);
    }

    public Mono<AiProviderAccount> requireEnabled(String name) {
        return get(name).filter(account -> account.getSpec() != null && account.getSpec().isEnabled());
    }

    public Mono<AiProviderAccount> firstEnabled(String providerName) {
        var options = ListOptions.builder().andQuery(equal("spec.provider", providerName)).build();
        return client.listAll(AiProviderAccount.class, options, defaultSort())
                .filter(account -> account.getSpec() != null && account.getSpec().isEnabled())
                .next();
    }

    public Mono<String> apiKey(AiProviderAccount account) {
        if (account == null
                || account.getSpec() == null
                || !StringUtils.hasText(account.getSpec().getApiKey())) {
            return Mono.empty();
        }
        return Mono.just(secretCipher.decrypt(account.getSpec().getApiKey()));
    }
}
