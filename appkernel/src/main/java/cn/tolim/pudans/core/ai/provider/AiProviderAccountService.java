package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiProviderAccountService {

    private final ReactiveExtensionClient client;

    private final AiProviderSecretCipher secretCipher;

    public Mono<AiProviderAccount> get(String name) {
        return client.get(AiProviderAccount.class, name);
    }

    public Mono<AiProviderAccount> requireEnabled(String name) {
        return get(name).filter(account -> account.getSpec() != null && account.getSpec().isEnabled());
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
