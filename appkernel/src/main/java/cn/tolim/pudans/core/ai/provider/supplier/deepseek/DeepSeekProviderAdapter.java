package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.provider.AbstractAiProviderAdapter;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
public class DeepSeekProviderAdapter extends AbstractAiProviderAdapter {

    private final DeepSeekMessagesCodec codec;

    private final DeepSeekMessagesParser parser;

    public DeepSeekProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, accountService);
        var requiredMapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.codec = new DeepSeekMessagesCodec(requiredMapper);
        this.parser = new DeepSeekMessagesParser(requiredMapper);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.DEEPSEEK;
    }

    @Override
    public Mono<AiChatResponse> chat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Mono.defer(() -> post(
                        provider,
                        account,
                        DeepSeekProtocol.MESSAGES_PATH,
                        serialize(request, model, false),
                        false,
                        fileHeaders(request))
                .map(parser::parse));
    }

    @Override
    public Flux<AiStreamEvent> streamChat(
            AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr request) {
        return Flux.defer(() -> postStream(
                        provider,
                        account,
                        DeepSeekProtocol.MESSAGES_PATH,
                        serialize(request, model, true),
                        fileHeaders(request))
                .concatMap(data -> Mono.justOrEmpty(parser.parseStreamEvent(data))));
    }

    @Override
    protected HttpHeaders buildHeaders(AiProvider provider, String apiKey, boolean stream) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(stream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.set(DeepSeekProtocol.API_KEY_HEADER, apiKey);
        }
        return headers;
    }

    private String serialize(AiChatIr request, AiModel model, boolean stream) {
        var prepared = AiProviderHttpSupport.withDefaultMaxTokens(
                AiProviderHttpSupport.withModelAndStream(request, model, stream), maxOutputTokens(model));
        return codec.serialize(prepared);
    }

    private static Map<String, String> fileHeaders(AiChatIr request) {
        return referencesFiles(request)
                ? Map.of(DeepSeekProtocol.BETA_HEADER, DeepSeekProtocol.FILES_BETA)
                : Map.of();
    }

    private static boolean referencesFiles(AiChatIr request) {
        for (var message : request.messages()) {
            for (var part : message.parts()) {
                if (part instanceof AiContentPart.File file && file.fileId() != null) {
                    return true;
                }
                if (part instanceof AiContentPart.Custom custom && fileSource(custom.raw())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fileSource(JsonNode raw) {
        var source = raw == null ? null : raw.get("source");
        return source != null && "file".equals(AiProviderHttpSupport.text(source, "type"));
    }

    private static Integer maxOutputTokens(AiModel model) {
        var spec = model == null ? null : model.getSpec();
        return spec == null ? null : spec.getMaxOutputTokens();
    }
}
