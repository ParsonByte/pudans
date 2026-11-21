package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LangChain4jEmbeddingModelFactory {

    private static final List<String> OPENAI_RESERVED_HEADERS =
            List.of("authorization", "content-type", "accept");

    private static final List<String> OLLAMA_RESERVED_HEADERS =
            List.of("content-type", "accept");

    public static boolean supports(AiProvider.Protocol protocol) {
        return protocol == AiProvider.Protocol.OPENAI_CHAT
                || protocol == AiProvider.Protocol.OPENAI_RESPONSES
                || protocol == AiProvider.Protocol.CUSTOM_HTTP
                || protocol == AiProvider.Protocol.GEMINI_OPENAI
                || protocol == AiProvider.Protocol.QIANWEN
                || protocol == AiProvider.Protocol.GEMINI
                || protocol == AiProvider.Protocol.OLLAMA;
    }

    public EmbeddingModel embeddingModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey,
            @Nullable Integer dimension) {
        return switch (protocol) {
            case OPENAI_CHAT, OPENAI_RESPONSES, CUSTOM_HTTP, GEMINI_OPENAI ->
                    openAiModel(provider, protocol, modelCode, apiKey, dimension);
            case QIANWEN -> qwenModel(provider, modelCode, requiredApiKey(apiKey, "千问"), dimension);
            case GEMINI -> geminiModel(provider, modelCode, requiredApiKey(apiKey, "Gemini"), dimension);
            case OLLAMA -> ollamaModel(provider, modelCode, dimension);
            default -> throw new AiProtocolException(
                    "供应商协议不支持 LangChain4j 嵌入模型：" + protocol);
        };
    }

    private EmbeddingModel openAiModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey,
            @Nullable Integer dimension) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(LangChain4jModelFactory.baseUrl(
                        provider, LangChain4jModelFactory.basePath(protocol)))
                .apiKey(optionalApiKey(apiKey))
                .modelName(modelCode)
                .dimensions(dimension)
                .customHeaders(headers(provider, OPENAI_RESERVED_HEADERS))
                .timeout(timeout(provider))
                .maxRetries(0)
                .build();
    }

    private EmbeddingModel qwenModel(
            AiProvider provider, String modelCode, String apiKey, @Nullable Integer dimension) {
        return QwenEmbeddingModel.builder()
                .baseUrl(LangChain4jModelFactory.baseUrl(provider, ""))
                .apiKey(apiKey)
                .modelName(modelCode)
                .dimension(dimension)
                .build();
    }

    private EmbeddingModel geminiModel(
            AiProvider provider, String modelCode, String apiKey, @Nullable Integer dimension) {
        return GoogleAiEmbeddingModel.builder()
                .baseUrl(LangChain4jModelFactory.baseUrl(provider, "/v1beta"))
                .apiKey(apiKey)
                .modelName(modelCode)
                .outputDimensionality(dimension)
                .timeout(timeout(provider))
                .maxRetries(0)
                .build();
    }

    private EmbeddingModel ollamaModel(
            AiProvider provider, String modelCode, @Nullable Integer dimension) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(LangChain4jModelFactory.baseUrl(provider, ""))
                .modelName(modelCode)
                .dimensions(dimension)
                .customHeaders(headers(provider, OLLAMA_RESERVED_HEADERS))
                .timeout(timeout(provider))
                .maxRetries(0)
                .build();
    }

    private static Map<String, String> headers(AiProvider provider, List<String> reserved) {
        var spec = provider == null ? null : provider.getSpec();
        var headers = spec == null ? null : spec.getHeaders();
        var filtered = new LinkedHashMap<String, String>();
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (StringUtils.hasText(name)
                        && value != null
                        && reserved.stream().noneMatch(name::equalsIgnoreCase)) {
                    filtered.put(name, value);
                }
            });
        }
        return filtered;
    }

    private static String optionalApiKey(String apiKey) {
        return StringUtils.hasText(apiKey) ? apiKey : null;
    }

    private static String requiredApiKey(String apiKey, String provider) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AiProtocolException(provider + " API Key 不能为空");
        }
        return apiKey;
    }

    private static Duration timeout(AiProvider provider) {
        var spec = provider == null ? null : provider.getSpec();
        var millis = spec == null ? null : spec.getReadTimeoutMs();
        return millis == null || millis <= 0 ? Duration.ofSeconds(60) : Duration.ofMillis(millis);
    }
}