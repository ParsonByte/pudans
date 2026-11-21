package cn.tolim.pudans.core.ai.provider.balance;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient.ProbeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Order(0)
@RequiredArgsConstructor
public class AiProviderBalanceProbeAdapter implements ProviderBalanceAdapter {

    private final AiProviderBalanceProbeClient probeClient;

    @Override
    public boolean supports(AiProvider provider, AiProviderAccount account) {
        return AiProviderBalanceProbeClient.configured(account);
    }

    @Override
    public Mono<ProbeResult> query(AiProvider provider, AiProviderAccount account) {
        return probeClient.probe(provider, account);
    }
}