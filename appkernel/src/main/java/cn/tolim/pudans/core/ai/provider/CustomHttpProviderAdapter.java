package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CustomHttpProviderAdapter extends OpenAiChatProviderAdapter {

    public CustomHttpProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, mapper, accountService);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.CUSTOM_HTTP;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            return super.chat(provider, account, model, request);
        });
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> {
            AiProviderConfigSupport.validate(provider);
            return super.streamChat(provider, account, model, request);
        });
    }
}
