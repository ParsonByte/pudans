package cn.tolim.pudans.core.ai.provider.balance;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient.ProbeResult;
import reactor.core.publisher.Mono;

public interface ProviderBalanceAdapter {

    boolean supports(AiProvider provider, AiProviderAccount account);

    Mono<ProbeResult> query(AiProvider provider, AiProviderAccount account);
}