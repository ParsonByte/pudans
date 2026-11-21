package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekModelsClient extends AbstractDeepSeekClient {

    static final String MODELS_PATH = "/models";

    static final String UPSTREAM_ERROR_CODE = "ai-provider-model-upstream-error";

    private static final String DEFAULT_ERROR = "DeepSeek 模型列表调用失败。";

    public DeepSeekModelsClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        super(webClient, mapper, providerAccounts, UPSTREAM_ERROR_CODE, DEFAULT_ERROR);
    }

    public Mono<ModelList> list(AiProvider provider, AiProviderAccount account) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .get()
                                .uri(AiProviderHttpSupport.endpoint(provider, MODELS_PATH))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("模型列表响应为空。", null))))
                        .map(this::parse))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    private ModelList parse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new AiProtocolException("DeepSeek 模型列表响应缺少 data 数组");
        }
        var models = new ArrayList<DeepSeekModel>();
        for (var item : data) {
            models.add(model(item));
        }
        return new ModelList(AiProviderHttpSupport.text(root, "object"), List.copyOf(models));
    }

    private static DeepSeekModel model(JsonNode item) {
        var id = AiProviderHttpSupport.text(item, "id");
        if (id == null) {
            throw new AiProtocolException("DeepSeek 模型响应缺少 id");
        }
        var effort = item.get("effort");
        return new DeepSeekModel(
                id,
                AiProviderHttpSupport.text(item, "object"),
                AiProviderHttpSupport.text(item, "owned_by"),
                AiProviderHttpSupport.text(item, "name"),
                AiProviderHttpSupport.integer(item, "context_window"),
                AiProviderHttpSupport.integer(item, "max_output_tokens"),
                stringList(item.get("input_modalities")),
                stringList(item.get("output_modalities")),
                new Effort(
                        stringList(effort == null ? null : effort.get("supported_levels")),
                        AiProviderHttpSupport.text(effort, "default_level")),
                AiProviderHttpSupport.value(item, "api_capabilities"));
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var item : node) {
            if (item.isTextual()) {
                values.add(item.textValue());
            }
        }
        return List.copyOf(values);
    }

    public record ModelList(String object, List<DeepSeekModel> data) {}

    public record DeepSeekModel(
            String id,
            String object,
            String ownedBy,
            String name,
            Integer contextWindow,
            Integer maxOutputTokens,
            List<String> inputModalities,
            List<String> outputModalities,
            Effort effort,
            JsonNode apiCapabilities) {}

    public record Effort(List<String> supportedLevels, String defaultLevel) {}
}
