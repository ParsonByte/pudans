package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.provider.AbstractAiProviderAdapter;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekChatProviderAdapter extends AbstractAiProviderAdapter {

    private final DeepSeekChatCodec codec;

    private final DeepSeekChatParser parser;

    public DeepSeekChatProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, accountService);
        var requiredMapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.codec = new DeepSeekChatCodec(requiredMapper);
        this.parser = new DeepSeekChatParser(requiredMapper);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.DEEPSEEK_CHAT;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> post(
                        provider, account, DeepSeekProtocol.CHAT_PATH, serialize(request, model, false), false)
                .map(parser::parse));
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> postStream(
                        provider, account, DeepSeekProtocol.CHAT_PATH, serialize(request, model, true))
                .concatMap(data -> Mono.justOrEmpty(parser.parseStreamEvent(data))));
    }

    @Override
    protected HttpHeaders buildHeaders(AiProvider provider, String apiKey, boolean stream) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(stream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    private String serialize(AiChatIr request, AiModel model, boolean stream) {
        var prepared = AiProviderHttpSupport.withDefaultMaxTokens(
                AiProviderHttpSupport.withModelAndStream(request, model, stream), maxOutputTokens(model));
        return codec.serialize(prepared);
    }

    private static Integer maxOutputTokens(AiModel model) {
        var spec = model == null ? null : model.getSpec();
        return spec == null ? null : spec.getMaxOutputTokens();
    }
}
