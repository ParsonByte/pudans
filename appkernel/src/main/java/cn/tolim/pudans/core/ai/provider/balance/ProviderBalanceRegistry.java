package cn.tolim.pudans.core.ai.provider.balance;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient.ProbeResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// 余额查询按适配器顺序尝试，前一个失败时回退到下一个供应商专用实现。
@Component
@RequiredArgsConstructor
public class ProviderBalanceRegistry {

    private final List<ProviderBalanceAdapter> adapters;

    public Mono<ProbeResult> query(AiProvider provider, AiProviderAccount account) {
        var candidates = adapters.stream()
                .filter(adapter -> adapter.supports(provider, account))
                .toList();
        if (candidates.isEmpty()) {
            return Mono.error(AiProviderBalanceProbeClient.notConfigured());
        }
        return attempt(provider, account, candidates, 0, null);
    }

    private static Mono<ProbeResult> attempt(
            AiProvider provider,
            AiProviderAccount account,
            List<ProviderBalanceAdapter> candidates,
            int index,
            @Nullable Throwable lastError) {
        if (index >= candidates.size()) {
            return lastError == null
                    ? Mono.error(AiProviderBalanceProbeClient.notConfigured())
                    : Mono.error(lastError);
        }
        return candidates
                .get(index)
                .query(provider, account)
                .onErrorResume(error -> attempt(provider, account, candidates, index + 1, error));
    }
}