package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AiProviderAdapter {

    AiProvider.Protocol protocol();

    Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request);

    Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request);
}
