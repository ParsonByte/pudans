package cn.tolim.pudans.core.ai.provider.supplier;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import reactor.core.publisher.Mono;

public interface SupplierWebSocketAccess {

    Mono<AiProvider> requireProvider(String name);

    Mono<AiProviderAccount> requireAccount(AiProvider provider);
}
