package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LangChain4jModelFactory {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final List<String> OPENAI_BASE_RESERVED_HEADERS =
            List.of("content-type", "accept");

    private static final List<String> ANTHROPIC_RESERVED_HEADERS =
            List.of("x-api-key", "anthropic-version", "content-type", "accept");

    private static final List<String> GEMINI_RESERVED_HEADERS =
            List.of("x-goog-api-key", "content-type", "accept");

    private static final List<String> OLLAMA_RESERVED_HEADERS =
            List.of("content-type", "accept");

    public ChatModel chatModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return switch (protocol) {
            case OPENAI_CHAT, GEMINI_OPENAI, QIANWEN, CUSTOM_HTTP, DEEPSEEK_CHAT, MINIMAX_CHAT ->
                    openAiChatModel(provider, protocol, modelCode, apiKey);
            case OPENAI_RESPONSES, DEEPSEEK_RESPONSES, MINIMAX_RESPONSES ->
                    openAiResponsesModel(provider, protocol, modelCode, apiKey);
            case ANTHROPIC, DEEPSEEK -> anthropicModel(provider, protocol, modelCode, apiKey);
            case MINIMAX -> minimaxMessagesModel(provider, modelCode, apiKey);
            case GEMINI -> geminiModel(provider, modelCode, apiKey);
            case OLLAMA -> ollamaModel(provider, modelCode);
            case MINIMAX_NATIVE -> throw new AiProtocolException(
                    "MiniMax Native 协议不支持 LangChain4j 模型。");
        };
    }

    public StreamingChatModel streamingChatModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return switch (protocol) {
            case OPENAI_CHAT, GEMINI_OPENAI, QIANWEN, CUSTOM_HTTP, DEEPSEEK_CHAT, MINIMAX_CHAT ->
                    openAiStreamingChatModel(provider, protocol, modelCode, apiKey);
            case OPENAI_RESPONSES, DEEPSEEK_RESPONSES, MINIMAX_RESPONSES ->
                    openAiResponsesStreamingChatModel(provider, protocol, modelCode, apiKey);
            case ANTHROPIC, DEEPSEEK -> anthropicStreamingChatModel(provider, protocol, modelCode, apiKey);
            case MINIMAX -> minimaxMessagesStreamingChatModel(provider, modelCode, apiKey);
            case GEMINI -> geminiStreamingModel(provider, modelCode, apiKey);
            case OLLAMA -> ollamaStreamingModel(provider, modelCode);
            case MINIMAX_NATIVE -> throw new AiProtocolException(
                    "MiniMax Native 协议不支持 LangChain4j 模型。");
        };
    }

    static String baseUrl(AiProvider provider, String pathPrefix) {
        var spec = provider == null ? null : provider.getSpec();
        var baseUrl = spec == null ? null : spec.getBaseUrl();
        if (!StringUtils.hasText(pathPrefix)) {
            var trimmed = AiProviderHttpSupport.requireHttpUrl(baseUrl, "供应商 Base URL");
            return trimmed.endsWith("/")
                    ? trimmed.substring(0, trimmed.length() - 1)
                    : trimmed;
        }
        return AiProviderHttpSupport.endpoint(provider, pathPrefix);
    }

    private ChatModel openAiChatModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(protocol)))
                .apiKey(optionalApiKey(apiKey))
                .modelName(modelCode)
                .customHeaders(headers(provider, openAiReservedHeaders(apiKey), Map.of()))
                .timeout(timeout(provider))
                .maxRetries(0)
                .returnThinking(true)
                .build();
    }

    private StreamingChatModel openAiStreamingChatModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(protocol)))
                .apiKey(optionalApiKey(apiKey))
                .modelName(modelCode)
                .customHeaders(headers(provider, openAiReservedHeaders(apiKey), Map.of()))
                .timeout(timeout(provider))
                .returnThinking(true)
                .accumulateToolCallId(accumulateToolCallId(protocol))
                .build();
    }

    private ChatModel openAiResponsesModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return OpenAiResponsesChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(protocol)))
                .apiKey(optionalApiKey(apiKey))
                .modelName(modelCode)
                .customHeaders(headers(provider, openAiReservedHeaders(apiKey), Map.of()))
                .build();
    }

    private StreamingChatModel openAiResponsesStreamingChatModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(protocol)))
                .apiKey(optionalApiKey(apiKey))
                .modelName(modelCode)
                .customHeaders(headers(provider, openAiReservedHeaders(apiKey), Map.of()))
                .build();
    }

    private ChatModel anthropicModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return AnthropicChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(protocol)))
                .apiKey(requiredApiKey(apiKey, "Anthropic"))
                .version(ANTHROPIC_VERSION)
                .modelName(modelCode)
                .customHeaders(headers(provider, ANTHROPIC_RESERVED_HEADERS, Map.of()))
                .timeout(timeout(provider))
                .maxRetries(0)
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    private StreamingChatModel anthropicStreamingChatModel(
            AiProvider provider,
            AiProvider.Protocol protocol,
            String modelCode,
            String apiKey) {
        return AnthropicStreamingChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(protocol)))
                .apiKey(requiredApiKey(apiKey, "Anthropic"))
                .version(ANTHROPIC_VERSION)
                .modelName(modelCode)
                .customHeaders(headers(provider, ANTHROPIC_RESERVED_HEADERS, Map.of()))
                .timeout(timeout(provider))
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    private ChatModel minimaxMessagesModel(
            AiProvider provider, String modelCode, String apiKey) {
        var key = requiredApiKey(apiKey, "MiniMax");
        return AnthropicChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(AiProvider.Protocol.MINIMAX)))
                .apiKey(key)
                .version(ANTHROPIC_VERSION)
                .modelName(modelCode)
                .customHeaders(headers(
                        provider, ANTHROPIC_RESERVED_HEADERS, Map.of("Authorization", "Bearer " + key)))
                .timeout(timeout(provider))
                .maxRetries(0)
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    private StreamingChatModel minimaxMessagesStreamingChatModel(
            AiProvider provider, String modelCode, String apiKey) {
        var key = requiredApiKey(apiKey, "MiniMax");
        return AnthropicStreamingChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(AiProvider.Protocol.MINIMAX)))
                .apiKey(key)
                .version(ANTHROPIC_VERSION)
                .modelName(modelCode)
                .customHeaders(headers(
                        provider, ANTHROPIC_RESERVED_HEADERS, Map.of("Authorization", "Bearer " + key)))
                .timeout(timeout(provider))
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    private ChatModel geminiModel(AiProvider provider, String modelCode, String apiKey) {
        return GoogleAiGeminiChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(AiProvider.Protocol.GEMINI)))
                .apiKey(requiredApiKey(apiKey, "Gemini"))
                .modelName(modelCode)
                .customHeaders(headers(provider, GEMINI_RESERVED_HEADERS, Map.of()))
                .timeout(timeout(provider))
                .maxRetries(0)
                .returnThinking(true)
                .build();
    }

    private StreamingChatModel geminiStreamingModel(
            AiProvider provider, String modelCode, String apiKey) {
        return GoogleAiGeminiStreamingChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(AiProvider.Protocol.GEMINI)))
                .apiKey(requiredApiKey(apiKey, "Gemini"))
                .modelName(modelCode)
                .customHeaders(headers(provider, GEMINI_RESERVED_HEADERS, Map.of()))
                .timeout(timeout(provider))
                .returnThinking(true)
                .build();
    }

    private ChatModel ollamaModel(AiProvider provider, String modelCode) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(AiProvider.Protocol.OLLAMA)))
                .modelName(modelCode)
                .customHeaders(headers(provider, OLLAMA_RESERVED_HEADERS, Map.of()))
                .timeout(timeout(provider))
                .maxRetries(0)
                .returnThinking(true)
                .build();
    }

    private StreamingChatModel ollamaStreamingModel(AiProvider provider, String modelCode) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl(provider, basePath(AiProvider.Protocol.OLLAMA)))
                .modelName(modelCode)
                .customHeaders(headers(provider, OLLAMA_RESERVED_HEADERS, Map.of()))
                .timeout(timeout(provider))
                .returnThinking(true)
                .build();
    }

    static String basePath(AiProvider.Protocol protocol) {
        return switch (protocol) {
            case OPENAI_CHAT, OPENAI_RESPONSES, ANTHROPIC, MINIMAX_CHAT, MINIMAX_RESPONSES,
                    CUSTOM_HTTP -> "/v1";
            case MINIMAX, DEEPSEEK -> "/anthropic/v1";
            case GEMINI_OPENAI, DEEPSEEK_CHAT, DEEPSEEK_RESPONSES, OLLAMA -> "";
            case QIANWEN -> "/compatible-mode/v1";
            case GEMINI -> "/v1beta";
            case MINIMAX_NATIVE -> "/v1/text/chatcompletion_v2";
        };
    }

    private static boolean accumulateToolCallId(AiProvider.Protocol protocol) {
        return switch (protocol) {
            case QIANWEN, DEEPSEEK_CHAT, MINIMAX_CHAT -> false;
            default -> true;
        };
    }

    static Map<String, String> headers(
            AiProvider provider, List<String> reserved, Map<String, String> extra) {
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
        filtered.putAll(extra);
        return filtered;
    }

    private static List<String> openAiReservedHeaders(String apiKey) {
        return StringUtils.hasText(apiKey)
                ? List.of("authorization", "content-type", "accept")
                : OPENAI_BASE_RESERVED_HEADERS;
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