package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiCompletionChunk;
import cn.tolim.pudans.core.ai.provider.AiCompletionRequest;
import cn.tolim.pudans.core.ai.provider.AiCompletionResult;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekCompletionClient extends AbstractDeepSeekClient {

    static final String COMPLETIONS_PATH = "/beta/completions";

    static final String UPSTREAM_ERROR_CODE = "ai-provider-completion-upstream-error";

    private static final String DEFAULT_ERROR = "FIM 补全服务调用失败。";

    private static final String DONE = "[DONE]";

    public DeepSeekCompletionClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        super(webClient, mapper, providerAccounts, UPSTREAM_ERROR_CODE, DEFAULT_ERROR);
    }

    public Mono<AiCompletionResult> complete(
            AiProvider provider, AiProviderAccount account, AiModel model, AiCompletionRequest request) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .post()
                                .uri(AiProviderHttpSupport.endpoint(provider, COMPLETIONS_PATH))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .bodyValue(body(model, request, false))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("补全响应为空。", null))))
                        .map(this::parseResult))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Flux<AiCompletionChunk> stream(
            AiProvider provider, AiProviderAccount account, AiModel model, AiCompletionRequest request) {
        return Flux.defer(() -> providerAccounts
                        .apiKey(account)
                        .defaultIfEmpty("")
                        .flatMapMany(apiKey -> webClient()
                                .post()
                                .uri(AiProviderHttpSupport.endpoint(provider, COMPLETIONS_PATH))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.TEXT_EVENT_STREAM)))
                                .bodyValue(body(model, request, true))
                                .retrieve()
                                .bodyToFlux(String.class)))
                .takeWhile(data -> !DONE.equals(data.strip()))
                .concatMap(data -> Mono.justOrEmpty(parseChunk(data)))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    private String body(AiModel model, AiCompletionRequest request, boolean stream) {
        var spec = model == null ? null : model.getSpec();
        var root = mapper.createObjectNode();
        if (spec != null && StringUtils.hasText(spec.getModelCode())) {
            root.put("model", spec.getModelCode());
        }
        root.put("prompt", request.prompt());
        if (request.suffix() != null) {
            root.put("suffix", request.suffix());
        }
        var maxTokens = request.maxTokens() != null
                ? request.maxTokens()
                : spec == null ? null : spec.getMaxOutputTokens();
        if (maxTokens != null) {
            root.put("max_tokens", maxTokens);
        }
        if (request.temperature() != null) {
            root.put("temperature", request.temperature());
        }
        if (request.topP() != null) {
            root.put("top_p", request.topP());
        }
        if (!request.stop().isEmpty()) {
            var stop = root.putArray("stop");
            request.stop().forEach(stop::add);
        }
        if (request.echo() != null) {
            root.put("echo", request.echo());
        }
        if (request.logprobs() != null) {
            root.put("logprobs", request.logprobs());
        }
        root.put("stream", stream);
        return AiProviderJsonSupport.write(mapper, root, DeepSeekProtocol.NAME);
    }

    private AiCompletionResult parseResult(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var choice = firstChoice(root);
        return new AiCompletionResult(
                AiProviderHttpSupport.text(root, "id"),
                AiProviderHttpSupport.text(root, "model"),
                AiProviderHttpSupport.text(choice, "text"),
                AiProviderHttpSupport.text(choice, "finish_reason"),
                usage(root.get("usage")));
    }

    private Optional<AiCompletionChunk> parseChunk(String data) {
        if (data == null || data.isBlank() || DONE.equals(data.strip())) {
            return Optional.empty();
        }
        var root = AiProviderHttpSupport.readObject(mapper, data.strip(), DeepSeekProtocol.NAME);
        var choice = firstChoice(root);
        var text = AiProviderHttpSupport.text(choice, "text");
        var finishReason = AiProviderHttpSupport.text(choice, "finish_reason");
        var usage = usage(root.get("usage"));
        if (text == null && finishReason == null && usage == null) {
            return Optional.empty();
        }
        return Optional.of(new AiCompletionChunk(text, finishReason, usage));
    }

    private static JsonNode firstChoice(ObjectNode root) {
        var choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new AiProtocolException("DeepSeek 补全响应缺少 choices");
        }
        return choices.get(0);
    }

    private static AiTokenUsage usage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        var input = AiProviderHttpSupport.integer(usageNode, "prompt_tokens");
        var output = AiProviderHttpSupport.integer(usageNode, "completion_tokens");
        var details = usageNode.get("prompt_tokens_details");
        var cacheRead = details != null && details.isObject()
                ? AiProviderHttpSupport.integer(details, "cached_tokens")
                : null;
        if (cacheRead == null) {
            cacheRead = AiProviderHttpSupport.integer(usageNode, "prompt_cache_hit_tokens");
        }
        if (input == null && output == null && cacheRead == null) {
            return null;
        }
        return new AiTokenUsage(input, output, cacheRead, null);
    }
}
