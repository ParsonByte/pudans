package cn.tolim.pudans.core.ai.provider;

import java.util.ArrayList;
import java.util.List;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class GenericModelsClient {

    private static final String CODE_UPSTREAM = "ai-provider-models-upstream-error";

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final ProviderAccounts providerAccounts;

    public GenericModelsClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        this.webClient = webClient;
        this.mapper = mapper;
        this.providerAccounts = providerAccounts;
    }

    public static boolean supports(AiProvider.Protocol protocol) {
        return protocol == AiProvider.Protocol.OPENAI_CHAT
                || protocol == AiProvider.Protocol.OPENAI_RESPONSES
                || protocol == AiProvider.Protocol.ANTHROPIC
                || protocol == AiProvider.Protocol.GEMINI_OPENAI
                || protocol == AiProvider.Protocol.QIANWEN
                || protocol == AiProvider.Protocol.MINIMAX_CHAT;
    }

    static String path(AiProvider.Protocol protocol) {
        return switch (protocol) {
            case GEMINI_OPENAI -> "/models";
            case QIANWEN -> "/compatible-mode/v1/models";
            default -> "/v1/models";
        };
    }

    public Mono<DeepSeekModelsClient.ModelList> list(
            AiProvider provider, AiProviderAccount account) {
        return Mono.defer(() -> providerAccounts
                        .apiKey(account)
                        .flatMap(apiKey -> webClient
                                .get()
                                .uri(AiProviderHttpSupport.endpoint(
                                        provider, path(provider.getSpec().getProtocol())))
                                .headers(headers -> applyHeaders(
                                        headers, provider, apiKey, provider.getSpec().getProtocol()))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(
                                        upstreamError("模型列表响应为空。", null))))
                        .map(this::parse))
                .onErrorMap(
                        error -> !(error instanceof ApiException),
                        error -> upstreamError("供应商模型列表获取失败。", error));
    }

    private static void applyHeaders(
            HttpHeaders headers,
            AiProvider provider,
            String apiKey,
            AiProvider.Protocol protocol) {
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (!StringUtils.hasText(apiKey)) {
            return;
        }
        if (protocol == AiProvider.Protocol.ANTHROPIC) {
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", ANTHROPIC_VERSION);
        } else {
            headers.setBearerAuth(apiKey);
        }
    }

    private DeepSeekModelsClient.ModelList parse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, "Model");
        var data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new AiProtocolException("模型列表响应缺少 data 数组");
        }
        var models = new ArrayList<DeepSeekModelsClient.DeepSeekModel>();
        for (var item : data) {
            var id = AiProviderHttpSupport.text(item, "id");
            if (!StringUtils.hasText(id)) {
                throw new AiProtocolException("模型列表响应缺少 id");
            }
            models.add(new DeepSeekModelsClient.DeepSeekModel(
                    id,
                    AiProviderHttpSupport.text(item, "object"),
                    AiProviderHttpSupport.text(item, "owned_by"),
                    firstText(item, "display_name", "name"),
                    firstInteger(item, "context_length", "context_window", "max_input_tokens"),
                    firstInteger(item, "max_output_tokens"),
                    List.of(),
                    List.of(),
                    null,
                    null));
        }
        return new DeepSeekModelsClient.ModelList(
                AiProviderHttpSupport.text(root, "object"), List.copyOf(models));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = AiProviderHttpSupport.text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static Integer firstInteger(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = AiProviderHttpSupport.integer(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ApiException upstreamError(String message, Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, message, cause);
    }
}
