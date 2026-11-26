package cn.tolim.pudans.core.ai.provider.deepseek;

import cn.tolim.pudans.contract.ai.AiModelType;
import cn.tolim.pudans.contract.ai.dto.AiProviderCapabilityCode;
import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviderCapabilities;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviderModel;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekProvider implements AiProvider {

    private static final String PROVIDER = "deepseek";

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    private static final AiProtocolProfile CHAT_COMPLETIONS =
            new DeepSeekChatCompletionsProfile();

    private static final AiProtocolProfile MESSAGES = new DeepSeekMessagesProfile();

    private static final AiProtocolProfile RESPONSES = new DeepSeekResponsesProfile();

    private static final Set<AiProviderCapabilityCode> CAPABILITIES = Set.of(
            AiProviderCapabilityCode.CHAT,
            AiProviderCapabilityCode.REASONING,
            AiProviderCapabilityCode.TOOLS,
            AiProviderCapabilityCode.VISION,
            AiProviderCapabilityCode.WEB_SEARCH,
            AiProviderCapabilityCode.FILES,
            AiProviderCapabilityCode.OCR,
            AiProviderCapabilityCode.TTS,
            AiProviderCapabilityCode.ASR,
            AiProviderCapabilityCode.BALANCE);

    private final AiProviderHttp http;

    public DeepSeekProvider(AiProviderHttp http) {
        this.http = http;
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return new AiProviderDescriptor(
                PROVIDER,
                "DeepSeek",
                "DeepSeek 官方 API，模型目录、对话、推理、文件、联网、语音与 OCR 能力均按官方接口适配。",
                "https://www.deepseek.com",
                "https://api-docs.deepseek.com",
                DEFAULT_BASE_URL,
                "chat-completions",
                List.of("chat-completions", "messages", "responses"),
                List.of("chat"),
                List.of(PROVIDER),
                false,
                AiProviderCapabilities.available(CAPABILITIES));
    }

    @Override
    public Mono<List<AiProviderModel>> discover(
            AiChannelEntity channel, Map<String, String> credentials) {
        var timeout = channel.getTimeoutMs() == null || channel.getTimeoutMs() <= 0
                ? http.responseTimeout()
                : Duration.ofMillis(channel.getTimeoutMs());
        return http.getJson(URI.create(modelsUrl(channel.getBaseUrl())), credentials, timeout)
                .map(DeepSeekProvider::models);
    }

    @Override
    public AiProtocolProfile protocolProfile(AiProtocol protocol) {
        return switch (protocol) {
            case CHAT_COMPLETIONS -> CHAT_COMPLETIONS;
            case MESSAGES -> MESSAGES;
            case RESPONSES -> RESPONSES;
            case SYSTEM_ONE -> null;
        };
    }

    @Override
    public boolean acceptsModel(String modelId) {
        return modelId != null
                && modelId.trim().toLowerCase(Locale.ROOT).startsWith(PROVIDER + "-");
    }

    @Override
    public String modelsUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/models";
    }

    @Override
    public String anthropicBaseUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/anthropic/v1";
    }

    @Override
    public String chatCompletionsBaseUrl(String baseUrl) {
        var normalized = resolveBaseUrl(baseUrl);
        return normalized.endsWith("/beta") ? normalized : apiBaseUrl(normalized);
    }

    String chatCompletionsUrl(AiChannelEntity channel) {
        return chatCompletionsBaseUrl(channel.getBaseUrl()) + "/chat/completions";
    }

    String messagesUrl(String baseUrl, boolean versioned) {
        var normalized = anthropicBaseUrl(baseUrl);
        return (versioned ? normalized : removeSuffix(normalized, "/v1")) + "/messages";
    }

    String filesUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/files";
    }

    String balanceUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/user/balance";
    }

    String responsesUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/responses";
    }

    String fimUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/beta/completions";
    }

    String toolsUrl(String baseUrl, String operation) {
        return apiBaseUrl(baseUrl) + "/v1/tools/" + operation;
    }

    String ttsWebSocketUrl(String baseUrl) {
        var uri = URI.create(apiBaseUrl(baseUrl));
        var scheme = "http".equalsIgnoreCase(uri.getScheme()) ? "ws" : "wss";
        return scheme + "://" + uri.getRawAuthority() + uri.getRawPath() + "/v1/tools/tts";
    }

    String apiBaseUrl(AiChannelEntity channel) {
        return apiBaseUrl(channel.getBaseUrl());
    }

    String apiBaseUrl(String baseUrl) {
        var normalized = resolveBaseUrl(baseUrl);
        for (var suffix : List.of("/anthropic/v1", "/anthropic", "/beta", "/v1")) {
            normalized = removeSuffix(normalized, suffix);
        }
        return normalized;
    }

    private static String removeSuffix(String value, String suffix) {
        return value.endsWith(suffix)
                ? value.substring(0, value.length() - suffix.length())
                : value;
    }

    private static List<AiProviderModel> models(String body) {
        try {
            if (!JsonUtils.mapper().readTree(body).path("data").isArray()) {
                throw new IllegalArgumentException("模型目录缺少 data 数组");
            }
            var response = JsonUtils.jsonToObject(body, DeepSeekModelsResponse.class);
            var models = new ArrayList<AiProviderModel>();
            for (var model : response.data()) {
                if (model == null || !StringUtils.hasText(model.id())) {
                    continue;
                }
                var inputModalities = strings(model.inputModalities());
                var outputModalities = strings(model.outputModalities());
                models.add(new AiProviderModel(
                        model.id(),
                        StringUtils.hasText(model.name()) ? model.name() : model.id(),
                        AiModelType.CHAT,
                        "provider",
                        positive(model.contextWindow()),
                        positive(model.maxOutputTokens()),
                        inputModalities,
                        outputModalities,
                        features(inputModalities, model.effort()),
                        catalog(model)));
            }
            return List.copyOf(models);
        } catch (Exception error) {
            throw AiProviderHttp.invalidResponse(error);
        }
    }

    private static List<String> features(
            List<String> inputModalities, Map<String, Object> effort) {
        var features = new ArrayList<String>();
        if (!supportedLevels(effort).isEmpty()) {
            features.add("reasoning");
        }
        if (inputModalities.contains("image")) {
            features.add("vision");
        }
        return List.copyOf(features);
    }

    private static Map<String, Object> catalog(DeepSeekModel model) {
        var values = new LinkedHashMap<String, Object>();
        values.put("provider", PROVIDER);
        values.put("model", model.id());
        values.put("source", "provider");
        if (StringUtils.hasText(model.ownedBy())) {
            values.put("ownedBy", model.ownedBy().trim());
        }
        var effort = effort(model.effort());
        if (!effort.isEmpty()) {
            values.put("effort", effort);
        }
        if (model.apiCapabilities() != null && !model.apiCapabilities().isEmpty()) {
            values.put("apiCapabilities", Map.copyOf(model.apiCapabilities()));
        }
        return Map.copyOf(values);
    }

    private static Map<String, Object> effort(Map<String, Object> effort) {
        var levels = supportedLevels(effort);
        if (levels.isEmpty()) {
            return Map.of();
        }
        var values = new LinkedHashMap<String, Object>();
        values.put("supportedLevels", levels);
        var defaultLevel = text(effort.get("default_level"));
        if (defaultLevel != null) {
            values.put("defaultLevel", defaultLevel);
        }
        return Map.copyOf(values);
    }

    private static List<String> supportedLevels(Map<String, Object> effort) {
        return effort == null ? List.of() : strings(effort.get("supported_levels"));
    }

    private static List<String> strings(List<?> values) {
        if (values == null) {
            return List.of();
        }
        var normalized = new ArrayList<String>();
        for (var value : values) {
            var text = text(value);
            if (text != null && !normalized.contains(text)) {
                normalized.add(text);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> strings(Object values) {
        return values instanceof List<?> list ? strings(list) : List.of();
    }

    private static String text(Object value) {
        return value instanceof String string && StringUtils.hasText(string)
                ? string.trim()
                : null;
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }
}
