package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderConfigSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Objects;
import org.reactivestreams.FlowAdapters;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class LangChain4jChat {

    private final ProviderAccounts providerAccounts;

    private final LangChain4jModelFactory modelFactory;

    private final AnthropicLangChain4jMapper anthropicMapper;

    private final OpenAiLangChain4jMapper openAiMapper;

    private final OpenAiResponsesLangChain4jMapper responsesMapper;

    private final GeminiLangChain4jChatMapper geminiMapper;

    private final OllamaLangChain4jChatMapper ollamaMapper;

    public LangChain4jChat(
            ProviderAccounts providerAccounts,
            LangChain4jModelFactory modelFactory,
            ObjectMapper mapper) {
        this.providerAccounts = Objects.requireNonNull(providerAccounts, "供应商账号服务不能为空");
        this.modelFactory = Objects.requireNonNull(modelFactory, "LangChain4j 模型工厂不能为空");
        var requiredMapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.anthropicMapper = new AnthropicLangChain4jMapper(requiredMapper);
        this.openAiMapper = new OpenAiLangChain4jMapper(requiredMapper);
        this.responsesMapper = new OpenAiResponsesLangChain4jMapper(requiredMapper);
        this.geminiMapper = new GeminiLangChain4jChatMapper(requiredMapper);
        this.ollamaMapper = new OllamaLangChain4jChatMapper(requiredMapper);
    }

    public Mono<AiChatResponse> chat(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiProvider.Protocol protocol,
            AiChatIr request) {
        return Mono.defer(() -> apiKey(account)
                .flatMap(apiKey -> {
                    AiProviderConfigSupport.validate(provider);
                    var mapper = mapper(protocol);
                    var modelCode = mapper.modelCode(model, request);
                    var chatRequest = mapper.toRequest(
                            request, modelCode, mapper.maxTokens(model, request));
                    var chatModel = modelFactory.chatModel(provider, protocol, modelCode, apiKey);
                    return invoke(chatModel, chatRequest);
                })
                .map(response -> mapper(protocol).toResponse(response)));
    }

    public Flux<AiStreamEvent> streamChat(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiProvider.Protocol protocol,
            AiChatIr request) {
        return Flux.defer(() -> apiKey(account)
                .flatMapMany(apiKey -> {
                    AiProviderConfigSupport.validate(provider);
                    var mapper = mapper(protocol);
                    var modelCode = mapper.modelCode(model, request);
                    var chatRequest = mapper.toRequest(
                            request, modelCode, mapper.maxTokens(model, request));
                    var chatModel = modelFactory.streamingChatModel(
                            provider, protocol, modelCode, apiKey);
                    return Flux.from(FlowAdapters.toPublisher(chatModel.chat(chatRequest)))
                            .subscribeOn(Schedulers.boundedElastic())
                            .concatMap(event -> Flux.fromStream(
                                    mapper.toStreamEvent(event).stream()));
                }));
    }

    private Mono<String> apiKey(AiProviderAccount account) {
        if (account == null) {
            return Mono.just("");
        }
        return providerAccounts.apiKey(account).defaultIfEmpty("");
    }

    private static Mono<ChatResponse> invoke(ChatModel chatModel, ChatRequest request) {
        return Mono.defer(() -> {
                    try {
                        return Mono.fromFuture(chatModel.chatAsync(request));
                    } catch (AsyncNotSupportedException error) {
                        return blocking(chatModel, request);
                    }
                })
                .onErrorResume(
                        AsyncNotSupportedException.class,
                        error -> blocking(chatModel, request));
    }

    private static Mono<ChatResponse> blocking(ChatModel chatModel, ChatRequest request) {
        return Mono.fromCallable(() -> chatModel.chat(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private LangChain4jChatMapper mapper(AiProvider.Protocol protocol) {
        return switch (protocol) {
            case ANTHROPIC, DEEPSEEK, MINIMAX -> anthropicMapper;
            case OPENAI_CHAT, GEMINI_OPENAI, QIANWEN, CUSTOM_HTTP, DEEPSEEK_CHAT,
                    MINIMAX_CHAT -> openAiMapper;
            case OPENAI_RESPONSES, DEEPSEEK_RESPONSES, MINIMAX_RESPONSES -> responsesMapper;
            case GEMINI -> geminiMapper;
            case OLLAMA -> ollamaMapper;
            case MINIMAX_NATIVE -> openAiMapper;
        };
    }
}