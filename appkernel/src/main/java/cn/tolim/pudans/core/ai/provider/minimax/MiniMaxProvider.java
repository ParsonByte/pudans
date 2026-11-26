package cn.tolim.pudans.core.ai.provider.minimax;

import cn.tolim.pudans.contract.ai.AiModelType;
import cn.tolim.pudans.contract.ai.dto.AiProviderCapabilityCode;
import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviderAuth;
import cn.tolim.pudans.core.ai.provider.AiProviderCapabilities;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviderModel;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
public class MiniMaxProvider implements AiProvider {

    static final String PROVIDER = "minimax";

    private static final String DEFAULT_BASE_URL = "https://api.minimax.cn";

    private static final AiProtocolProfile MESSAGES = new MiniMaxMessagesProfile();

    private static final AiProtocolProfile CHAT_COMPLETIONS =
            new MiniMaxChatCompletionsProfile();

    private static final AiProtocolProfile RESPONSES = new MiniMaxResponsesProfile();

    private static final Set<AiProviderCapabilityCode> CAPABILITIES = Set.of(
            AiProviderCapabilityCode.CHAT,
            AiProviderCapabilityCode.REASONING,
            AiProviderCapabilityCode.TOOLS,
            AiProviderCapabilityCode.VISION,
            AiProviderCapabilityCode.WEB_SEARCH,
            AiProviderCapabilityCode.FILES,
            AiProviderCapabilityCode.TTS,
            AiProviderCapabilityCode.ASR,
            AiProviderCapabilityCode.IMAGE,
            AiProviderCapabilityCode.VIDEO,
            AiProviderCapabilityCode.AUDIO);

    private final AiProviderHttp http;

    public MiniMaxProvider(AiProviderHttp http) {
        this.http = http;
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return new AiProviderDescriptor(
                PROVIDER,
                "MiniMax",
                "MiniMax 开放平台，按官方接口适配模型目录、文本、推理、文件、语音、"
                        + "图像、视频、音乐与音色能力。",
                "https://www.minimaxi.com",
                "https://platform.minimaxi.com/docs",
                DEFAULT_BASE_URL,
                "messages",
                List.of("messages", "chat-completions", "responses"),
                List.of("chat", "image", "video", "audio"),
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
        var openAi = http.getJson(
                        URI.create(modelsUrl(channel.getBaseUrl())), credentials, timeout)
                .map(MiniMaxProvider::models);
        var anthropic = http.getJson(
                        URI.create(anthropicModelsUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        timeout,
                        AiProviderAuth.X_API_KEY)
                .map(MiniMaxProvider::models)
                .onErrorResume(error -> Mono.just(List.of()));
        return Mono.zip(openAi, anthropic)
                .map(tuple -> merged(
                        merged(tuple.getT1(), tuple.getT2()), MiniMaxCatalog.mediaModels()));
    }

    @Override
    public AiProtocolProfile protocolProfile(AiProtocol protocol) {
        return switch (protocol) {
            case MESSAGES -> MESSAGES;
            case CHAT_COMPLETIONS -> CHAT_COMPLETIONS;
            case RESPONSES -> RESPONSES;
            case SYSTEM_ONE -> null;
        };
    }

    @Override
    public boolean acceptsModel(String modelId) {
        if (modelId == null) {
            return false;
        }
        var value = modelId.trim().toLowerCase(Locale.ROOT);
        return value.startsWith(PROVIDER + "-")
                || value.startsWith("speech-")
                || value.startsWith("image-")
                || value.startsWith("asr-")
                || value.startsWith("music-")
                || value.startsWith("hailuo")
                || value.startsWith("t2v-")
                || value.startsWith("i2v-")
                || value.startsWith("s2v-")
                || value.startsWith("fl2v-");
    }

    @Override
    public boolean verifiesModelInList(String modelId) {
        return modelId != null
                && modelId.trim().toLowerCase(Locale.ROOT).startsWith("minimax-m");
    }

    @Override
    public String modelsUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/models";
    }

    String anthropicModelsUrl(String baseUrl) {
        return anthropicBaseUrl(baseUrl) + "/models";
    }

    @Override
    public String anthropicBaseUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/anthropic/v1";
    }

    @Override
    public String chatCompletionsBaseUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1";
    }

    @Override
    public String responsesBaseUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1";
    }

    String messagesUrl(String baseUrl) {
        return anthropicBaseUrl(baseUrl) + "/messages";
    }

    String chatCompletionsUrl(String baseUrl) {
        return chatCompletionsBaseUrl(baseUrl) + "/chat/completions";
    }

    String responsesUrl(String baseUrl) {
        return responsesBaseUrl(baseUrl) + "/responses";
    }

    String responsesInputTokensUrl(String baseUrl) {
        return responsesBaseUrl(baseUrl) + "/responses/input_tokens";
    }

    String filesUploadUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/files/upload";
    }

    String filesListUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/files/list";
    }

    String filesRetrieveUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/files/retrieve";
    }

    String filesContentUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/files/retrieve_content";
    }

    String filesDeleteUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/files/delete";
    }

    String ttsUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/t2a_v2";
    }

    String asrUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/speech_to_text";
    }

    String speechAsyncUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/t2a_async_v2";
    }

    String speechTaskUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/query/t2a_async_query_v2";
    }

    public String ttsWebSocketUrl(String baseUrl, boolean bidirectional) {
        var base = URI.create(apiBaseUrl(baseUrl));
        var scheme = "http".equalsIgnoreCase(base.getScheme()) ? "ws" : "wss";
        return scheme
                + "://"
                + base.getRawAuthority()
                + "/ws/v1/"
                + (bidirectional ? "t2a_v2_bidi" : "t2a_v2");
    }

    String imageGenerationsUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/images/generations";
    }

    String imageEditsUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/images/edits";
    }

    String imageContextUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/h3_image_context_ir";
    }

    String legacyImageUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/image_generation";
    }

    String videoGenerationUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v2/video_generation";
    }

    String legacyVideoUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/video_generation";
    }

    String legacyVideoQueryUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/query/video_generation";
    }

    String videoAgentUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/video_template_generation";
    }

    String videoAgentQueryUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/query/video_template_generation";
    }

    String videoListUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v2/query/video_generation";
    }

    String videoContextUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v2/h3_context_ir";
    }

    String videoRegenerationUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v2/video_regeneration";
    }

    String musicGenerationUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/music_generation";
    }

    String musicCoverUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/music_cover_preprocess";
    }

    String lyricsGenerationUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/lyrics_generation";
    }

    String voicesUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/get_voice";
    }

    String voiceDeleteUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/delete_voice";
    }

    String voiceCloneUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/voice_clone";
    }

    String voiceDesignUrl(String baseUrl) {
        return apiBaseUrl(baseUrl) + "/v1/voice_design";
    }

    String apiBaseUrl(String baseUrl) {
        var normalized = resolveBaseUrl(baseUrl);
        for (var suffix : List.of("/anthropic/v1", "/anthropic", "/v1")) {
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
            var response = JsonUtils.jsonToObject(body, MiniMaxModelsResponse.class);
            var models = new LinkedHashMap<String, AiProviderModel>();
            for (var model : response.data()) {
                if (model == null || !StringUtils.hasText(model.id())) {
                    continue;
                }
                var id = model.id().trim();
                models.putIfAbsent(
                        id.toLowerCase(Locale.ROOT),
                        MiniMaxCatalog.enrich(new AiProviderModel(
                                id,
                                StringUtils.hasText(model.displayName())
                                        ? model.displayName().trim()
                                        : displayName(id),
                                modelType(id),
                                "provider",
                                null,
                                null,
                                List.of(),
                                List.of(),
                                List.of(),
                                catalog(model))));
            }
            return List.copyOf(models.values());
        } catch (Exception error) {
            throw AiProviderHttp.invalidResponse(error);
        }
    }

    private static List<AiProviderModel> merged(
            List<AiProviderModel> primary, List<AiProviderModel> compatibility) {
        var models = new LinkedHashMap<String, AiProviderModel>();
        for (var model : primary) {
            models.putIfAbsent(model.modelId().toLowerCase(Locale.ROOT), model);
        }
        for (var model : compatibility) {
            models.putIfAbsent(model.modelId().toLowerCase(Locale.ROOT), model);
        }
        return List.copyOf(models.values());
    }

    // 模型列表只返回标识时恢复厂商规范大小写用于展示。
    private static String displayName(String modelId) {
        var normalized = modelId.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minimax-") && modelId.length() > PROVIDER.length() + 1) {
            var suffix = modelId.substring(PROVIDER.length() + 1);
            return "MiniMax-" + Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
        }
        return modelId;
    }

    // 模型列表只返回标识，按官方模型命名规则归属模型类型。
    private static AiModelType modelType(String modelId) {
        var value = modelId.toLowerCase(Locale.ROOT);
        if (value.contains("image") || value.startsWith("image-")) {
            return AiModelType.IMAGE;
        }
        if (value.startsWith("minimax-h3")
                || value.contains("hailuo")
                || value.contains("video")
                || value.contains("t2v")
                || value.contains("i2v")
                || value.contains("fl2v")
                || value.contains("s2v")) {
            return AiModelType.VIDEO;
        }
        if (value.startsWith("speech-")
                || value.contains("music")
                || value.startsWith("asr")
                || value.contains("audio")
                || value.contains("tts")
                || value.contains("whisper")) {
            return AiModelType.AUDIO;
        }
        if (value.contains("embedding")) {
            return AiModelType.EMBEDDING;
        }
        if (value.contains("rerank")) {
            return AiModelType.RERANK;
        }
        return AiModelType.CHAT;
    }

    private static Map<String, Object> catalog(MiniMaxModel model) {
        var values = new LinkedHashMap<String, Object>();
        values.put("provider", PROVIDER);
        values.put("model", model.id().trim());
        values.put("source", "provider");
        if (model.created() != null) {
            values.put("created", model.created());
        }
        if (StringUtils.hasText(model.ownedBy())) {
            values.put("ownedBy", model.ownedBy().trim());
        }
        return Map.copyOf(values);
    }
}
