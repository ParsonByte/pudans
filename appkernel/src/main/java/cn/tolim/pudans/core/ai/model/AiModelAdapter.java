package cn.tolim.pudans.core.ai.model;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

public interface AiModelAdapter {

    Set<AiProvider.Protocol> protocols();

    Set<AiModel.Capability> capabilities();

    Mono<AiModelResponse> invoke(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request);

    default Mono<AiModelResponse> poll(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModel.Capability capability,
            String providerTaskId,
            @Nullable Map<String, ?> payload) {
        return Mono.error(new UnsupportedOperationException("供应商不支持任务轮询"));
    }
}
