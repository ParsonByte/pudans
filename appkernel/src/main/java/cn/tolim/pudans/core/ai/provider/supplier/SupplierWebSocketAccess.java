package cn.tolim.pudans.core.ai.provider.supplier;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import reactor.core.publisher.Mono;

public interface SupplierWebSocketAccess {

    Mono<AiProvider> requireProvider(String name);

    Mono<AiProviderAccount> requireAccount(AiProvider provider);
}
