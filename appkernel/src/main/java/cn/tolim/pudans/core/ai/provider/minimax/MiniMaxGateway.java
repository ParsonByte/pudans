package cn.tolim.pudans.core.ai.provider.minimax;

import cn.tolim.pudans.contract.ai.dto.AiFile;
import cn.tolim.pudans.contract.ai.dto.AiFileDeletion;
import cn.tolim.pudans.contract.ai.dto.AiFilePage;
import cn.tolim.pudans.contract.ai.dto.AiImageContextResult;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiImageInput;
import cn.tolim.pudans.contract.ai.dto.AiImageSubjectReference;
import cn.tolim.pudans.contract.ai.dto.AiLegacyImageRequest;
import cn.tolim.pudans.contract.ai.dto.AiLegacyImageResult;
import cn.tolim.pudans.contract.ai.dto.AiLegacyVideoRequest;
import cn.tolim.pudans.contract.ai.dto.AiLegacyVideoTask;
import cn.tolim.pudans.contract.ai.dto.AiLyricsGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiLyricsGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiMusicCoverRequest;
import cn.tolim.pudans.contract.ai.dto.AiMusicCoverResult;
import cn.tolim.pudans.contract.ai.dto.AiMusicGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiMusicGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiSpeechAsyncRequest;
import cn.tolim.pudans.contract.ai.dto.AiSpeechRequest;
import cn.tolim.pudans.contract.ai.dto.AiSpeechTask;
import cn.tolim.pudans.contract.ai.dto.AiSpeechTaskSubmitted;
import cn.tolim.pudans.contract.ai.dto.AiTranscriptionResult;
import cn.tolim.pudans.contract.ai.dto.AiTranscriptionSegment;
import cn.tolim.pudans.contract.ai.dto.AiVideoAgentInput;
import cn.tolim.pudans.contract.ai.dto.AiVideoAgentRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoAgentTask;
import cn.tolim.pudans.contract.ai.dto.AiVideoContent;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationPage;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationTask;
import cn.tolim.pudans.contract.ai.dto.AiVideoRegenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskDeleted;
import cn.tolim.pudans.contract.ai.dto.AiVideoSubjectReference;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskSubmitted;
import cn.tolim.pudans.contract.ai.dto.AiVoiceCloneRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceCloneResult;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDeleteRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDeletion;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDesignRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDesignResult;
import cn.tolim.pudans.contract.ai.dto.AiVoiceListRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceListResult;
import cn.tolim.pudans.core.ai.provider.AiCapabilityGateway;
import cn.tolim.pudans.core.ai.provider.AiProviderAuth;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviderMultipart;
import cn.tolim.pudans.core.ai.provider.AiSpeech;
import cn.tolim.pudans.core.ai.provider.AiTranscriptionOptions;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MiniMaxGateway implements AiCapabilityGateway {

    private static final String PROVIDER = "minimax";

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final Set<String> UPLOAD_PURPOSES = Set.of(
            "voice_clone",
            "prompt_audio",
            "t2a_async_input",
            "video_understanding",
            "video_generation_input",
            "image_generation_input");

    private static final Set<String> LIST_PURPOSES = Set.of(
            "voice_clone",
            "prompt_audio",
            "t2a_async_input",
            "video_generation_input",
            "image_generation_input");

    private static final Set<String> ASR_FORMATS = Set.of("json", "verbose_json", "srt", "vtt");

    private static final Set<String> TTS_FORMATS =
            Set.of("mp3", "pcm", "flac", "wav", "pcmu_raw", "pcmu_wav", "opus");

    private static final Set<Integer> TTS_SAMPLE_RATES = Set.of(8000, 16000, 22050, 24000, 32000, 44100);

    private static final Set<Integer> TTS_BITRATES = Set.of(32000, 64000, 128000, 256000);

    private static final Set<String> TTS_EMOTIONS = Set.of(
            "happy", "sad", "angry", "fearful", "disgusted", "surprised", "calm", "fluent", "whisper");

    private static final Set<String> IMAGE_FORMATS = Set.of("png", "jpeg", "jpg", "webp");

    private static final Set<String> IMAGE_RESPONSE_FORMATS = Set.of("url", "b64_json");

    private static final Set<String> IMAGE_AREAS = Set.of("1K", "2K", "4K");

    private static final Set<String> VIDEO_RESOLUTIONS = Set.of("480P", "768P", "2K");

    private static final Set<String> VIDEO_RATIOS =
            Set.of("adaptive", "21:9", "16:9", "4:3", "1:1", "3:4", "9:16");

    private static final Set<String> MUSIC_FORMATS = Set.of("mp3", "wav", "pcm");

    private static final Set<Integer> MUSIC_SAMPLE_RATES = Set.of(16000, 24000, 32000, 44100);

    private static final Set<Integer> MUSIC_BITRATES = Set.of(32000, 64000, 128000, 256000);

    private static final Set<String> OUTPUT_FORMATS = Set.of("url", "hex");

    private static final Set<String> VOICE_TYPES =
            Set.of("system", "voice_cloning", "voice_generation", "all");

    private static final Set<String> DELETABLE_VOICE_TYPES =
            Set.of("voice_cloning", "voice_generation");

    private static final Set<String> LYRICS_MODES = Set.of("write_full_song", "edit");

    private static final Pattern VOICE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{6,254}[A-Za-z0-9]");

    private static final int MAX_TTS_CHARACTERS = 10_000;

    private final AiProviderHttp http;

    private final MiniMaxProvider provider;

    public MiniMaxGateway(AiProviderHttp http, MiniMaxProvider provider) {
        this.http = http;
        this.provider = provider;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Mono<AiFile> uploadFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String purpose,
            String filename,
            String contentType,
            Path content,
            Long expiresAfterSeconds) {
        return Mono.defer(() -> {
            var normalizedPurpose = requiredChoice(purpose, UPLOAD_PURPOSES, "文件用途");
            if (expiresAfterSeconds != null) {
                throw invalid("该渠道不支持文件有效期。");
            }
            var normalizedFilename = AiProviderMultipart.filename(filename);
            if (AiProviderMultipart.empty(content)) {
                throw invalid("上传文件不能为空。");
            }
            var parts = new LinkedMultiValueMap<String, Object>();
            parts.add("purpose", normalizedPurpose);
            parts.add(
                    "file",
                    AiProviderMultipart.filePart(normalizedFilename, contentType, content));
            return http.postMultipart(
                            URI.create(provider.filesUploadUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            parts,
                            timeout(channel))
                    .map(body -> {
                        requireSuccess(body);
                        return parse(body, MiniMaxFileResponse.class).file();
                    })
                    .map(MiniMaxGateway::file);
        });
    }

    @Override
    public Mono<AiFilePage> listFiles(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String after,
            Integer limit,
            String order,
            String purpose) {
        return Mono.defer(() -> {
            var builder = UriComponentsBuilder.fromUriString(
                    provider.filesListUrl(channel.getBaseUrl()));
            if (StringUtils.hasText(purpose)) {
                builder.queryParam(
                        "purpose", requiredChoice(purpose, LIST_PURPOSES, "文件用途"));
            }
            return http.getJson(
                            builder.build().encode().toUri(),
                            credentials,
                            Map.of(),
                            timeout(channel))
                    .map(body -> {
                        requireSuccess(body);
                        return parse(body, MiniMaxFileListResponse.class);
                    })
                    .map(response -> new AiFilePage(
                            "list",
                            response.files().stream().map(MiniMaxGateway::file).toList(),
                            null,
                            null,
                            false));
        });
    }

    @Override
    public Mono<AiFile> retrieveFile(
            AiChannelEntity channel, Map<String, String> credentials, String fileId) {
        return Mono.defer(() -> http.getJson(
                        fileQuery(provider.filesRetrieveUrl(channel.getBaseUrl()), fileId),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(body -> {
                    requireSuccess(body);
                    return parse(body, MiniMaxFileResponse.class).file();
                })
                .map(MiniMaxGateway::file));
    }

    @Override
    public Flux<DataBuffer> downloadFile(
            AiChannelEntity channel, Map<String, String> credentials, String fileId) {
        return Flux.defer(() -> http.getBinaryStream(
                fileQuery(provider.filesContentUrl(channel.getBaseUrl()), fileId),
                credentials,
                Map.of(),
                timeout(channel),
                AiProviderAuth.BEARER));
    }

    @Override
    public Mono<AiFileDeletion> deleteFile(
            AiChannelEntity channel, Map<String, String> credentials, String fileId) {
        return retrieveFile(channel, credentials, fileId).flatMap(file -> {
            var body = Map.<String, Object>of(
                    "file_id", fileIdLong(fileId), "purpose", file.purpose());
            return http.postJson(
                            URI.create(provider.filesDeleteUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            body,
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        var deleted = parse(response, MiniMaxFileDeletionResponse.class);
                        var id = deleted.fileId() == null
                                ? fileId.trim()
                                : String.valueOf(deleted.fileId());
                        return new AiFileDeletion(id, "file", true);
                    });
        });
    }

    @Override
    public Mono<AiSpeech> synthesize(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiSpeechRequest request) {
        return Mono.defer(() -> {
            if (request == null || !StringUtils.hasText(request.text())) {
                throw invalid("语音合成文本不能为空。");
            }
            var text = request.text();
            if (characters(text) > MAX_TTS_CHARACTERS) {
                throw invalid("语音合成文本不能超过 10000 个字符。");
            }
            var model = requiredText(request.model(), "语音模型");
            var voice = requiredText(request.voiceId(), "语音音色");
            var format = request.format() == null || !StringUtils.hasText(request.format())
                    ? "mp3"
                    : requiredChoice(request.format(), TTS_FORMATS, "音频格式");
            var body = speechBody(request, model, text, voice, format);
            var stream = Boolean.TRUE.equals(request.stream());
            var contentType = contentType(format);
            if (stream) {
                body.put("stream", true);
                body.put("stream_options", Map.of("exclude_aggregated_audio", true));
                var audio = http.postSse(
                                URI.create(provider.ttsUrl(channel.getBaseUrl())),
                                credentials,
                                Map.of(),
                                body,
                                timeout(channel))
                        .mapNotNull(ServerSentEvent::data)
                        .filter(StringUtils::hasText)
                        .flatMap(data -> {
                            requireSuccess(data);
                            var chunk = tree(data).path("data").path("audio").asText("");
                            return chunk.isEmpty()
                                    ? Flux.<byte[]>empty()
                                    : Flux.just(hex(chunk));
                        })
                        .<DataBuffer>map(
                                bytes -> DefaultDataBufferFactory.sharedInstance.wrap(bytes));
                return Mono.just(new AiSpeech(contentType, audio));
            }
            body.put("stream", false);
            return http.postJson(
                            URI.create(provider.ttsUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            body,
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        var speech = parse(response, MiniMaxSpeechResponse.class);
                        var audio = speech.data() == null ? "" : speech.data().audio();
                        if (!StringUtils.hasText(audio)) {
                            throw invalid("供应商未返回合成音频。");
                        }
                        return new AiSpeech(
                                contentType,
                                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(hex(audio))));
                    });
        });
    }

    @Override
    public Mono<AiTranscriptionResult> transcribe(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String filename,
            String contentType,
            Path content,
            AiTranscriptionOptions options) {
        return Mono.defer(() -> {
            if (AiProviderMultipart.empty(content)) {
                throw invalid("音频文件不能为空。");
            }
            var model = options == null || options.modelOrNull() == null
                    ? "asr-1.0"
                    : options.modelOrNull();
            var responseFormat = options == null || options.responseFormatOrNull() == null
                    ? "json"
                    : requiredChoice(options.responseFormatOrNull(), ASR_FORMATS, "语音识别返回格式");
            var parts = new LinkedMultiValueMap<String, Object>();
            parts.add("model", model);
            parts.add("file", AiProviderMultipart.filePart(
                    AiProviderMultipart.filename(filename), contentType, content));
            parts.add("response_format", responseFormat);
            if (options != null && options.timestampLevelOrNull() != null) {
                parts.add("timestamp_level", options.timestampLevelOrNull());
            }
            var headers = new LinkedHashMap<String, String>();
            if (options != null && options.languageOrNull() != null) {
                headers.put("language", options.languageOrNull());
            }
            return http.postMultipart(
                            URI.create(provider.asrUrl(channel.getBaseUrl())),
                            credentials,
                            Map.copyOf(headers),
                            parts,
                            timeout(channel))
                    .map(body -> transcription(body, responseFormat));
        });
    }

    public Mono<AiSpeechTaskSubmitted> speechAsyncCreate(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiSpeechAsyncRequest request) {
        return Mono.defer(() -> {
            if (request == null) {
                throw invalid("请求体不能为空。");
            }
            var model = requiredText(request.model(), "语音模型");
            var text = trimmed(request.text());
            if (text != null && request.textFileId() != null) {
                throw invalid("合成文本与文本文件不能同时提供。");
            }
            if (text == null && request.textFileId() == null) {
                throw invalid("合成文本或文本文件必须提供其一。");
            }
            if (text != null && billedCharacters(text) > 100_000) {
                throw invalid("异步语音合成文本不能超过 100000 个计费字符。");
            }
            if (request.voiceSetting() == null || request.voiceSetting().isEmpty()) {
                throw invalid("音色设置不能为空。");
            }
            var body = new LinkedHashMap<String, Object>();
            body.put("model", model);
            body.put("voice_setting", request.voiceSetting());
            if (text != null) {
                body.put("text", text);
            }
            if (request.textFileId() != null) {
                body.put("text_file_id", request.textFileId());
            }
            if (request.audioSetting() != null && !request.audioSetting().isEmpty()) {
                body.put("audio_setting", request.audioSetting());
            }
            if (request.pronunciationDict() != null && !request.pronunciationDict().isEmpty()) {
                body.put("pronunciation_dict", request.pronunciationDict());
            }
            putIfText(body, "language_boost", request.languageBoost());
            if (request.voiceModify() != null && !request.voiceModify().isEmpty()) {
                body.put("voice_modify", request.voiceModify());
            }
            if (request.aigcWatermark() != null) {
                body.put("aigc_watermark", request.aigcWatermark());
            }
            return http.postJson(
                            URI.create(provider.speechAsyncUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            Map.copyOf(body),
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiSpeechTaskSubmitted.class);
                    });
        });
    }

    public Mono<AiSpeechTask> speechAsyncTask(
            AiChannelEntity channel, Map<String, String> credentials, String taskId) {
        return Mono.defer(() -> http.getJson(
                        queryUri(provider.speechTaskUrl(channel.getBaseUrl()), "task_id", taskId),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(response -> {
                    requireSuccess(response);
                    return parse(response, AiSpeechTask.class);
                }));
    }

    public Mono<AiMusicCoverResult> musicCover(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiMusicCoverRequest request) {
        return Mono.defer(() -> {
            if (request == null) {
                throw invalid("请求体不能为空。");
            }
            var model = requiredText(request.model(), "音乐模型");
            if (!"music-cover".equalsIgnoreCase(model)) {
                throw invalid("翻唱前处理仅支持 music-cover 模型。");
            }
            var audioUrl = trimmed(request.audioUrl());
            var audioBase64 = trimmed(request.audioBase64());
            if ((audioUrl == null) == (audioBase64 == null)) {
                throw invalid("参考音频地址和音频内容必须且只能提供其一。");
            }
            var body = new LinkedHashMap<String, Object>();
            body.put("model", model);
            if (audioUrl != null) {
                body.put("audio_url", audioUrl);
            }
            if (audioBase64 != null) {
                body.put("audio_base64", audioBase64);
            }
            return http.postJson(
                            URI.create(provider.musicCoverUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            Map.copyOf(body),
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiMusicCoverResult.class);
                    });
        });
    }

    public Mono<String> responsesInputTokens(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body) {
        return http.postJson(
                URI.create(provider.responsesInputTokensUrl(channel.getBaseUrl())),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    @Override
    public Mono<String> chatCompletions(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body) {
        return http.postJson(
                URI.create(provider.chatCompletionsUrl(channel.getBaseUrl())),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    @Override
    public Flux<ServerSentEvent<String>> chatCompletionsStream(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body) {
        return http.postSse(
                URI.create(provider.chatCompletionsUrl(channel.getBaseUrl())),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    @Override
    public Mono<String> messages(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta,
            boolean versioned) {
        return http.postJson(
                URI.create(provider.messagesUrl(channel.getBaseUrl())),
                credentials,
                messagesHeaders(beta),
                body,
                timeout(channel),
                AiProviderAuth.BEARER);
    }

    @Override
    public Flux<ServerSentEvent<String>> messagesStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta,
            boolean versioned) {
        return http.postSse(
                URI.create(provider.messagesUrl(channel.getBaseUrl())),
                credentials,
                messagesHeaders(beta),
                body,
                timeout(channel),
                AiProviderAuth.BEARER);
    }

    @Override
    public Mono<String> responses(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body) {
        return http.postJson(
                URI.create(provider.responsesUrl(channel.getBaseUrl())),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    @Override
    public Flux<ServerSentEvent<String>> responsesStream(
            AiChannelEntity channel, Map<String, String> credentials, Map<String, Object> body) {
        return http.postSse(
                URI.create(provider.responsesUrl(channel.getBaseUrl())),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    public Mono<AiImageGenerationResult> imageGenerations(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiImageGenerationRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.imageGenerationsUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        imageBody(request, false),
                        timeout(channel))
                .map(body -> parse(body, AiImageGenerationResult.class)));
    }

    public Flux<ServerSentEvent<String>> imageGenerationsStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiImageGenerationRequest request) {
        return Flux.defer(() -> {
            var body = imageBody(request, false);
            body.put("stream", true);
            return http.postSse(
                    URI.create(provider.imageGenerationsUrl(channel.getBaseUrl())),
                    credentials,
                    Map.of(),
                    body,
                    timeout(channel));
        });
    }

    public Mono<AiImageGenerationResult> imageEdits(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiImageGenerationRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.imageEditsUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        imageBody(request, true),
                        timeout(channel))
                .map(body -> parse(body, AiImageGenerationResult.class)));
    }

    public Flux<ServerSentEvent<String>> imageEditsStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiImageGenerationRequest request) {
        return Flux.defer(() -> {
            var body = imageBody(request, true);
            body.put("stream", true);
            return http.postSse(
                    URI.create(provider.imageEditsUrl(channel.getBaseUrl())),
                    credentials,
                    Map.of(),
                    body,
                    timeout(channel));
        });
    }

    public Mono<AiImageContextResult> imageContext(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiImageGenerationRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.imageContextUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        imageContextBody(request),
                        timeout(channel))
                .map(body -> parse(body, AiImageContextResult.class)));
    }

    public Mono<AiLegacyImageResult> legacyImageGeneration(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiLegacyImageRequest request) {
        return Mono.defer(() -> {
            var body = legacyImageBody(request);
            return http.postJson(
                            URI.create(provider.legacyImageUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            body,
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiLegacyImageResult.class);
                    });
        });
    }

    public Mono<AiVideoTaskSubmitted> legacyVideoGeneration(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiLegacyVideoRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.legacyVideoUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        legacyVideoBody(request),
                        timeout(channel))
                .map(response -> {
                    requireSuccess(response);
                    return parse(response, AiVideoTaskSubmitted.class);
                }));
    }

    public Mono<AiLegacyVideoTask> legacyVideoTask(
            AiChannelEntity channel, Map<String, String> credentials, String taskId) {
        return Mono.defer(() -> http.getJson(
                        queryUri(provider.legacyVideoQueryUrl(channel.getBaseUrl()), "task_id", taskId),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(response -> {
                    requireSuccess(response);
                    return parse(response, AiLegacyVideoTask.class);
                }));
    }

    public Mono<AiVideoTaskSubmitted> videoAgentGeneration(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVideoAgentRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.videoAgentUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        videoAgentBody(request),
                        timeout(channel))
                .map(response -> {
                    requireSuccess(response);
                    return parse(response, AiVideoTaskSubmitted.class);
                }));
    }

    public Mono<AiVideoAgentTask> videoAgentTask(
            AiChannelEntity channel, Map<String, String> credentials, String taskId) {
        return Mono.defer(() -> http.getJson(
                        queryUri(provider.videoAgentQueryUrl(channel.getBaseUrl()), "task_id", taskId),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(response -> {
                    requireSuccess(response);
                    return parse(response, AiVideoAgentTask.class);
                }));
    }

    public Mono<AiVideoTaskSubmitted> videoGeneration(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVideoGenerationRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.videoGenerationUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        videoBody(request),
                        timeout(channel))
                .map(body -> parse(body, AiVideoTaskSubmitted.class)));
    }

    public Mono<AiVideoGenerationTask> videoTask(
            AiChannelEntity channel, Map<String, String> credentials, String taskId) {
        return Mono.defer(() -> http.getJson(
                        videoTaskUri(channel, taskId),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(MiniMaxGateway::videoTask));
    }

    public Mono<AiVideoGenerationPage> videoTasks(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Integer pageNumber,
            Integer pageSize,
            String status,
            List<String> taskIds,
            String model,
            String taskType) {
        return Mono.defer(() -> http.getJson(
                        videoListUri(
                                channel,
                                pageNumber,
                                pageSize,
                                status,
                                taskIds,
                                model,
                                taskType),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(body -> parse(body, AiVideoGenerationPage.class)));
    }

    public Mono<AiVideoTaskDeleted> deleteVideoTask(
            AiChannelEntity channel, Map<String, String> credentials, String taskId) {
        return Mono.defer(() -> http.deleteJson(
                        videoDeleteUri(channel, taskId),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(body -> parse(body, AiVideoTaskDeleted.class)));
    }

    public Mono<AiVideoTaskSubmitted> videoContext(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVideoGenerationRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.videoContextUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        videoContextBody(request),
                        timeout(channel))
                .map(body -> parse(body, AiVideoTaskSubmitted.class)));
    }

    public Mono<AiVideoTaskSubmitted> videoRegeneration(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVideoRegenerationRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.videoRegenerationUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        videoRegenerationBody(request),
                        timeout(channel))
                .map(body -> parse(body, AiVideoTaskSubmitted.class)));
    }

    public Mono<AiMusicGenerationResult> musicGeneration(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiMusicGenerationRequest request) {
        return Mono.defer(() -> http.postJson(
                        URI.create(provider.musicGenerationUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        musicBody(request),
                        timeout(channel))
                .map(response -> musicResult(response)));
    }

    public Flux<ServerSentEvent<String>> musicGenerationStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiMusicGenerationRequest request) {
        return Flux.defer(() -> {
            var body = musicBody(request);
            body.put("stream", true);
            return http.postSse(
                    URI.create(provider.musicGenerationUrl(channel.getBaseUrl())),
                    credentials,
                    Map.of(),
                    body,
                    timeout(channel));
        });
    }

    public Mono<AiLyricsGenerationResult> lyricsGeneration(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiLyricsGenerationRequest request) {
        return Mono.defer(() -> {
            if (request == null) {
                throw invalid("请求体不能为空。");
            }
            var mode = requiredChoice(request.mode(), LYRICS_MODES, "歌词生成模式");
            var body = new LinkedHashMap<String, Object>();
            body.put("mode", mode);
            putIfText(body, "prompt", request.prompt());
            putIfText(body, "lyrics", request.lyrics());
            putIfText(body, "title", request.title());
            return http.postJson(
                            URI.create(provider.lyricsGenerationUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            Map.copyOf(body),
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiLyricsGenerationResult.class);
                    });
        });
    }

    public Mono<AiVoiceListResult> voiceList(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVoiceListRequest request) {
        return Mono.defer(() -> {
            var voiceType = request == null
                    ? "all"
                    : requiredChoice(request.voiceType(), VOICE_TYPES, "音色类型");
            return http.postJson(
                            URI.create(provider.voicesUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            Map.of("voice_type", voiceType),
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiVoiceListResult.class);
                    });
        });
    }

    public Mono<AiVoiceDeletion> voiceDelete(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVoiceDeleteRequest request) {
        return Mono.defer(() -> {
            if (request == null) {
                throw invalid("请求体不能为空。");
            }
            var voiceType =
                    requiredChoice(request.voiceType(), DELETABLE_VOICE_TYPES, "音色类型");
            var voiceId = requiredText(request.voiceId(), "音色标识");
            return http.postJson(
                            URI.create(provider.voiceDeleteUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            Map.of("voice_type", voiceType, "voice_id", voiceId),
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiVoiceDeletion.class);
                    });
        });
    }

    public Mono<AiVoiceCloneResult> voiceClone(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVoiceCloneRequest request) {
        return Mono.defer(() -> {
            if (request == null || request.fileId() == null) {
                throw invalid("音色复刻文件不能为空。");
            }
            var voiceId = requiredText(request.voiceId(), "音色标识");
            if (!VOICE_ID.matcher(voiceId).matches()) {
                throw invalid("音色标识必须以字母开头，仅包含字母、数字、- 和 _，"
                        + "长度 8 到 256。");
            }
            var body = new LinkedHashMap<String, Object>();
            body.put("file_id", request.fileId());
            body.put("voice_id", voiceId);
            if (request.clonePrompt() != null) {
                var prompt = new LinkedHashMap<String, Object>();
                if (request.clonePrompt().promptAudio() != null) {
                    prompt.put("prompt_audio", request.clonePrompt().promptAudio());
                }
                putIfText(prompt, "prompt_text", request.clonePrompt().promptText());
                if (!prompt.isEmpty()) {
                    body.put("clone_prompt", Map.copyOf(prompt));
                }
            }
            if (StringUtils.hasText(request.text())) {
                body.put("text", request.text().trim());
                body.put("model", requiredText(request.model(), "试听语音模型"));
            }
            putIfText(body, "language_boost", request.languageBoost());
            putIfText(body, "text_validation", request.textValidation());
            if (request.accuracy() != null) {
                if (request.accuracy() < 0 || request.accuracy() > 1) {
                    throw invalid("ASR 相似度阈值必须在 0 到 1 之间。");
                }
                body.put("accuracy", request.accuracy());
            }
            if (request.needNoiseReduction() != null) {
                body.put("need_noise_reduction", request.needNoiseReduction());
            }
            if (request.needVolumeNormalization() != null) {
                body.put("need_volume_normalization", request.needVolumeNormalization());
            }
            if (request.aigcWatermark() != null) {
                body.put("aigc_watermark", request.aigcWatermark());
            }
            return http.postJson(
                            URI.create(provider.voiceCloneUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            Map.copyOf(body),
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiVoiceCloneResult.class);
                    });
        });
    }

    public Mono<AiVoiceDesignResult> voiceDesign(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiVoiceDesignRequest request) {
        return Mono.defer(() -> {
            if (request == null) {
                throw invalid("请求体不能为空。");
            }
            var body = new LinkedHashMap<String, Object>();
            body.put("prompt", requiredText(request.prompt(), "音色描述"));
            body.put("preview_text", requiredText(request.previewText(), "试听文本"));
            putIfText(body, "voice_id", request.voiceId());
            if (request.aigcWatermark() != null) {
                body.put("aigc_watermark", request.aigcWatermark());
            }
            return http.postJson(
                            URI.create(provider.voiceDesignUrl(channel.getBaseUrl())),
                            credentials,
                            Map.of(),
                            Map.copyOf(body),
                            timeout(channel))
                    .map(response -> {
                        requireSuccess(response);
                        return parse(response, AiVoiceDesignResult.class);
                    });
        });
    }

    private static Map<String, Object> speechBody(
            AiSpeechRequest request, String model, String text, String voice, String format) {
        var voiceSetting = new LinkedHashMap<String, Object>();
        voiceSetting.put("voice_id", voice);
        if (request.speed() != null) {
            if (request.speed() < 0.5 || request.speed() > 2) {
                throw invalid("语音语速必须在 0.5 到 2 之间。");
            }
            voiceSetting.put("speed", request.speed());
        }
        if (request.volume() != null) {
            if (request.volume() <= 0 || request.volume() > 10) {
                throw invalid("语音音量必须大于 0 且不超过 10。");
            }
            voiceSetting.put("vol", request.volume());
        }
        if (request.pitch() != null) {
            if (request.pitch() < -12 || request.pitch() > 12) {
                throw invalid("语音语调必须在 -12 到 12 之间。");
            }
            voiceSetting.put("pitch", request.pitch());
        }
        if (StringUtils.hasText(request.emotion())) {
            voiceSetting.put(
                    "emotion", requiredChoice(request.emotion(), TTS_EMOTIONS, "语音情绪"));
        }
        var audioSetting = new LinkedHashMap<String, Object>();
        audioSetting.put("format", format);
        if (request.sampleRate() != null) {
            if (!TTS_SAMPLE_RATES.contains(request.sampleRate())) {
                throw invalid("语音采样率不受支持：" + request.sampleRate() + "。");
            }
            audioSetting.put("sample_rate", request.sampleRate());
        }
        if (request.bitrate() != null) {
            if (!TTS_BITRATES.contains(request.bitrate())) {
                throw invalid("语音比特率不受支持：" + request.bitrate() + "。");
            }
            audioSetting.put("bitrate", request.bitrate());
        }
        if (request.channel() != null) {
            if (request.channel() != 1 && request.channel() != 2) {
                throw invalid("语音声道数只能是 1 或 2。");
            }
            audioSetting.put("channel", request.channel());
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("text", text);
        body.put("voice_setting", Map.copyOf(voiceSetting));
        body.put("audio_setting", Map.copyOf(audioSetting));
        putIfText(body, "language_boost", request.languageBoost());
        if (request.subtitleEnable() != null) {
            body.put("subtitle_enable", request.subtitleEnable());
        }
        return body;
    }

    private static Map<String, Object> legacyImageBody(AiLegacyImageRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", requiredText(request.model(), "图片模型"));
        body.put("prompt", requiredText(request.prompt(), "图片描述"));
        if (!request.subjectReference().isEmpty()) {
            body.put("subject_reference", legacyImageSubjects(request.subjectReference()));
        }
        if (!request.style().isEmpty()) {
            body.put("style", request.style());
        }
        putIfText(body, "aspect_ratio", request.aspectRatio());
        if (request.width() != null || request.height() != null) {
            body.put("width", legacyImageDimension(request.width(), "宽"));
            body.put("height", legacyImageDimension(request.height(), "高"));
        }
        if (StringUtils.hasText(request.responseFormat())) {
            body.put("response_format", requiredChoice(
                    request.responseFormat(), Set.of("url", "base64"), "图片返回格式"));
        }
        if (request.seed() != null) {
            body.put("seed", request.seed());
        }
        if (request.n() != null) {
            if (request.n() < 1 || request.n() > 9) {
                throw invalid("生成图片数量必须在 1 到 9 之间。");
            }
            body.put("n", request.n());
        }
        if (request.promptOptimizer() != null) {
            body.put("prompt_optimizer", request.promptOptimizer());
        }
        if (request.aigcWatermark() != null) {
            body.put("aigc_watermark", request.aigcWatermark());
        }
        return body;
    }

    private static List<Map<String, Object>> legacyImageSubjects(
            List<AiImageSubjectReference> subjects) {
        var values = new ArrayList<Map<String, Object>>();
        for (var subject : subjects) {
            if (subject == null) {
                throw invalid("主体参考不能为空。");
            }
            var type = requiredText(subject.type(), "主体类型");
            if (!"character".equalsIgnoreCase(type)) {
                throw invalid("主体类型仅支持 character。");
            }
            values.add(Map.of(
                    "type", "character",
                    "image_file", requiredText(subject.imageFile(), "主体参考图")));
        }
        return List.copyOf(values);
    }

    private static int legacyImageDimension(Integer value, String label) {
        if (value == null) {
            throw invalid("图片宽度和高度必须同时提供。");
        }
        if (value < 512 || value > 2048 || value % 8 != 0) {
            throw invalid("图片" + label + "必须在 512 到 2048 之间且是 8 的倍数。");
        }
        return value;
    }

    private static Map<String, Object> legacyVideoBody(AiLegacyVideoRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", requiredText(request.model(), "视频模型"));
        body.put("prompt", requiredText(request.prompt(), "视频描述"));
        if (request.promptOptimizer() != null) {
            body.put("prompt_optimizer", request.promptOptimizer());
        }
        if (request.fastPretreatment() != null) {
            body.put("fast_pretreatment", request.fastPretreatment());
        }
        if (request.duration() != null) {
            body.put("duration", request.duration());
        }
        putIfText(body, "resolution", request.resolution());
        putIfText(body, "callback_url", request.callbackUrl());
        if (request.aigcWatermark() != null) {
            body.put("aigc_watermark", request.aigcWatermark());
        }
        putIfText(body, "first_frame_image", request.firstFrameImage());
        putIfText(body, "last_frame_image", request.lastFrameImage());
        if (!request.subjectReference().isEmpty()) {
            body.put("subject_reference", videoSubjects(request.subjectReference()));
        }
        return body;
    }

    private static List<Map<String, Object>> videoSubjects(
            List<AiVideoSubjectReference> subjects) {
        var values = new ArrayList<Map<String, Object>>();
        for (var subject : subjects) {
            if (subject == null) {
                throw invalid("主体参考不能为空。");
            }
            var type = requiredText(subject.type(), "主体类型");
            if (!"character".equalsIgnoreCase(type)) {
                throw invalid("主体类型仅支持 character。");
            }
            if (subject.image().isEmpty()) {
                throw invalid("主体参考图不能为空。");
            }
            var images = new ArrayList<String>();
            for (var image : subject.image()) {
                images.add(requiredText(image, "主体参考图"));
            }
            values.add(Map.of("type", "character", "image", List.copyOf(images)));
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> videoAgentBody(AiVideoAgentRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("template_id", requiredText(request.templateId(), "视频模板"));
        if (!request.textInputs().isEmpty()) {
            body.put("text_inputs", agentInputs(request.textInputs()));
        }
        if (!request.mediaInputs().isEmpty()) {
            body.put("media_inputs", agentInputs(request.mediaInputs()));
        }
        putIfText(body, "callback_url", request.callbackUrl());
        return body;
    }

    private static List<Map<String, Object>> agentInputs(List<AiVideoAgentInput> inputs) {
        var values = new ArrayList<Map<String, Object>>();
        for (var input : inputs) {
            if (input == null) {
                throw invalid("模板输入不能为空。");
            }
            values.add(Map.of("value", requiredText(input.value(), "模板输入")));
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> imageBody(
            AiImageGenerationRequest request, boolean requireImages) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", requiredText(request.model(), "图片模型"));
        body.put("prompt", requiredText(request.prompt(), "图片描述"));
        if (request.disablePromptEnhanced() != null) {
            body.put("disable_prompt_enhanced", request.disablePromptEnhanced());
        }
        if (request.n() != null) {
            if (request.n() < 1 || request.n() > 10) {
                throw invalid("生成图片数量必须在 1 到 10 之间。");
            }
            body.put("n", request.n());
        }
        putIfText(body, "size", request.size());
        putIfText(body, "aspect_ratio", request.aspectRatio());
        if (StringUtils.hasText(request.area())) {
            body.put("area", requiredChoice(request.area(), IMAGE_AREAS, "输出面积档位"));
        }
        if (StringUtils.hasText(request.outputFormat())) {
            body.put(
                    "output_format",
                    requiredChoice(request.outputFormat(), IMAGE_FORMATS, "图片输出格式"));
        }
        if (StringUtils.hasText(request.responseFormat())) {
            body.put(
                    "response_format",
                    requiredChoice(
                            request.responseFormat(), IMAGE_RESPONSE_FORMATS, "图片返回格式"));
        }
        if (request.outputCompression() != null) {
            if (request.outputCompression() < 0 || request.outputCompression() > 100) {
                throw invalid("图片压缩质量必须在 0 到 100 之间。");
            }
            body.put("output_compression", request.outputCompression());
        }
        if (request.aigcWatermark() != null) {
            body.put("aigc_watermark", request.aigcWatermark());
        }
        if (!request.images().isEmpty()) {
            body.put("images", imageInputs(request.images()));
        } else if (requireImages) {
            throw invalid("图片编辑至少需要一张参考图。");
        }
        return body;
    }

    private static Map<String, Object> imageContextBody(AiImageGenerationRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", requiredText(request.model(), "图片模型"));
        body.put("prompt", requiredText(request.prompt(), "图片描述"));
        putIfText(body, "size", request.size());
        putIfText(body, "aspect_ratio", request.aspectRatio());
        if (StringUtils.hasText(request.area())) {
            body.put("area", requiredChoice(request.area(), IMAGE_AREAS, "输出面积档位"));
        }
        if (!request.images().isEmpty()) {
            body.put("images", imageInputs(request.images()));
        }
        return body;
    }

    private static List<Map<String, Object>> imageInputs(List<AiImageInput> images) {
        var values = new ArrayList<Map<String, Object>>();
        for (var image : images) {
            var url = image == null ? null : trimmed(image.imageUrl());
            if (url == null) {
                throw invalid("参考图地址不能为空。");
            }
            values.add(Map.of("image_url", url));
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> videoBody(AiVideoGenerationRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = videoContentBody(request.model(), request.content());
        body.put("resolution", videoResolution(request.resolution()));
        if (request.duration() == null || request.duration() < 4 || request.duration() > 15) {
            throw invalid("视频时长必须在 4 到 15 秒之间。");
        }
        body.put("duration", request.duration());
        putIfText(body, "ratio", videoRatio(request.ratio()));
        if (!request.extra().isEmpty()) {
            body.put("extra", request.extra());
        }
        putVideoOptions(body, request.generateAudio(), request.callbackUrl(), request.aigcWatermark());
        return body;
    }

    private static Map<String, Object> videoContextBody(AiVideoGenerationRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = videoContentBody(request.model(), request.content());
        if (request.duration() == null || request.duration() < 4 || request.duration() > 15) {
            throw invalid("视频时长必须在 4 到 15 秒之间。");
        }
        body.put("duration", request.duration());
        putIfText(body, "ratio", videoRatio(request.ratio()));
        return body;
    }

    private static Map<String, Object> videoRegenerationBody(AiVideoRegenerationRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", requiredText(request.model(), "视频模型"));
        body.put("resolution", videoResolution(request.resolution()));
        var sourceTaskId = trimmed(request.sourceTaskId());
        if (sourceTaskId != null) {
            body.put("source_task_id", sourceTaskId);
        }
        if (!request.content().isEmpty()) {
            body.putAll(videoContentBody(request.model(), request.content()));
        }
        if (sourceTaskId == null && request.content().isEmpty()) {
            throw invalid("视频再生成需要源任务或输入内容。");
        }
        putVideoOptions(body, request.generateAudio(), request.callbackUrl(), request.aigcWatermark());
        return body;
    }

    private static Map<String, Object> videoContentBody(
            String model, List<AiVideoContent> content) {
        var values = new ArrayList<Map<String, Object>>();
        var hasText = false;
        for (var item : content) {
            if (item == null || !StringUtils.hasText(item.type())) {
                throw invalid("视频内容项类型不能为空。");
            }
            var value = new LinkedHashMap<String, Object>();
            value.put("type", item.type().trim());
            if (StringUtils.hasText(item.text())) {
                value.put("text", item.text());
                hasText = true;
            }
            if (item.imageUrl() != null) {
                value.put("image_url", Map.of("url", requiredText(item.imageUrl().url(), "图片地址")));
            }
            if (item.videoUrl() != null) {
                value.put("video_url", Map.of("url", requiredText(item.videoUrl().url(), "视频地址")));
            }
            if (item.audioUrl() != null) {
                value.put("audio_url", Map.of("url", requiredText(item.audioUrl().url(), "音频地址")));
            }
            putIfText(value, "role", item.role());
            values.add(Map.copyOf(value));
        }
        if (values.isEmpty()) {
            throw invalid("视频内容不能为空。");
        }
        if (!hasText) {
            throw invalid("视频内容必须包含文本提示词。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", requiredText(model, "视频模型"));
        body.put("content", List.copyOf(values));
        return body;
    }

    private static void putVideoOptions(
            Map<String, Object> body,
            Boolean generateAudio,
            String callbackUrl,
            Boolean aigcWatermark) {
        if (generateAudio != null) {
            body.put("generate_audio", generateAudio);
        }
        putIfText(body, "callback_url", callbackUrl);
        if (aigcWatermark != null) {
            body.put("aigc_watermark", aigcWatermark);
        }
    }

    private static String videoResolution(String value) {
        var resolution = requiredText(value, "视频分辨率").toUpperCase(Locale.ROOT);
        if (!VIDEO_RESOLUTIONS.contains(resolution)) {
            throw invalid("视频分辨率不受支持：" + value + "。");
        }
        return resolution;
    }

    private static String videoRatio(String ratio) {
        if (!StringUtils.hasText(ratio)) {
            return null;
        }
        return requiredChoice(ratio, VIDEO_RATIOS, "视频宽高比");
    }

    private static Map<String, Object> musicBody(AiMusicGenerationRequest request) {
        if (request == null) {
            throw invalid("请求体不能为空。");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", requiredText(request.model(), "音乐模型"));
        putIfText(body, "prompt", request.prompt());
        putIfText(body, "lyrics", request.lyrics());
        var outputFormat = StringUtils.hasText(request.outputFormat())
                ? requiredChoice(request.outputFormat(), OUTPUT_FORMATS, "音频返回格式")
                : "hex";
        body.put("output_format", outputFormat);
        if (Boolean.TRUE.equals(request.stream()) && !"hex".equals(outputFormat)) {
            throw invalid("流式音乐生成只支持 hex 返回格式。");
        }
        if (request.audioSetting() != null) {
            var setting = new LinkedHashMap<String, Object>();
            if (StringUtils.hasText(request.audioSetting().format())) {
                setting.put(
                        "format",
                        requiredChoice(request.audioSetting().format(), MUSIC_FORMATS, "音频格式"));
            }
            if (request.audioSetting().sampleRate() != null) {
                if (!MUSIC_SAMPLE_RATES.contains(request.audioSetting().sampleRate())) {
                    throw invalid("音乐采样率不受支持：" + request.audioSetting().sampleRate() + "。");
                }
                setting.put("sample_rate", request.audioSetting().sampleRate());
            }
            if (request.audioSetting().bitrate() != null) {
                if (!MUSIC_BITRATES.contains(request.audioSetting().bitrate())) {
                    throw invalid("音乐比特率不受支持：" + request.audioSetting().bitrate() + "。");
                }
                setting.put("bitrate", request.audioSetting().bitrate());
            }
            body.put("audio_setting", Map.copyOf(setting));
        }
        if (request.aigcWatermark() != null) {
            body.put("aigc_watermark", request.aigcWatermark());
        }
        if (request.lyricsOptimizer() != null) {
            body.put("lyrics_optimizer", request.lyricsOptimizer());
        }
        if (request.isInstrumental() != null) {
            body.put("is_instrumental", request.isInstrumental());
        }
        putIfText(body, "audio_url", request.audioUrl());
        putIfText(body, "audio_base64", request.audioBase64());
        putIfText(body, "cover_feature_id", request.coverFeatureId());
        return body;
    }

    private static AiMusicGenerationResult musicResult(String body) {
        requireSuccess(body);
        return parse(body, AiMusicGenerationResult.class);
    }

    private static AiTranscriptionResult transcription(String body, String responseFormat) {
        if ("srt".equals(responseFormat) || "vtt".equals(responseFormat)) {
            return new AiTranscriptionResult(null, body, null);
        }
        var response = parse(body, MiniMaxAsrResponse.class);
        var durationMs = response.duration() == null
                ? null
                : (int) Math.round(response.duration() * 1000);
        var segments = new ArrayList<AiTranscriptionSegment>();
        for (var segment : response.segments()) {
            segments.add(new AiTranscriptionSegment(
                    segment.id(), segment.start(), segment.end(), segment.speaker(), segment.text()));
        }
        return new AiTranscriptionResult(
                response.traceId(), response.text(), durationMs, response.speakers(), segments);
    }

    private static AiVideoGenerationTask videoTask(String body) {
        var root = tree(body);
        var node = root.has("task") ? root.path("task") : root;
        try {
            return JsonUtils.mapper().treeToValue(node, AiVideoGenerationTask.class);
        } catch (Exception error) {
            throw AiProviderHttp.invalidRequestResponse(error);
        }
    }

    private URI videoDeleteUri(AiChannelEntity channel, String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw invalid("视频任务标识不能为空。");
        }
        return UriComponentsBuilder.fromUriString(
                        provider.videoGenerationUrl(channel.getBaseUrl()))
                .pathSegment(taskId.trim())
                .build()
                .encode()
                .toUri();
    }

    private URI videoTaskUri(AiChannelEntity channel, String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw invalid("视频任务标识不能为空。");
        }
        return UriComponentsBuilder.fromUriString(provider.videoListUrl(channel.getBaseUrl()))
                .pathSegment(taskId.trim())
                .build()
                .encode()
                .toUri();
    }

    private URI videoListUri(
            AiChannelEntity channel,
            Integer pageNumber,
            Integer pageSize,
            String status,
            List<String> taskIds,
            String model,
            String taskType) {
        var builder = UriComponentsBuilder.fromUriString(
                provider.videoListUrl(channel.getBaseUrl()));
        if (pageNumber != null) {
            builder.queryParam("page_num", pageNumber);
        }
        if (pageSize != null) {
            builder.queryParam("page_size", pageSize);
        }
        putIfText(builder, "filter.status", status);
        if (taskIds != null) {
            for (var taskId : taskIds) {
                if (StringUtils.hasText(taskId)) {
                    builder.queryParam("filter.task_ids", taskId.trim());
                }
            }
        }
        putIfText(builder, "filter.model", model);
        putIfText(builder, "filter.task_type", taskType);
        return builder.build().encode().toUri();
    }

    private static URI fileQuery(String url, String fileId) {
        return queryUri(url, "file_id", fileId);
    }

    private static URI queryUri(String url, String name, String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid("请求标识不能为空。");
        }
        return UriComponentsBuilder.fromUriString(url)
                .queryParam(name, value.trim())
                .build()
                .encode()
                .toUri();
    }

    private static long fileIdLong(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw invalid("文件标识不能为空。");
        }
        try {
            return Long.parseLong(fileId.trim());
        } catch (NumberFormatException error) {
            throw invalid("文件标识必须是整数。");
        }
    }

    private static AiFile file(MiniMaxFile file) {
        if (file == null || file.fileId() == null) {
            throw AiProviderHttp.invalidRequestResponse(
                    new IllegalArgumentException("缺少文件对象"));
        }
        return new AiFile(
                String.valueOf(file.fileId()),
                "file",
                file.bytes(),
                file.createdAt(),
                file.filename(),
                file.purpose(),
                null);
    }

    private static Map<String, String> messagesHeaders(String beta) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        if (StringUtils.hasText(beta)) {
            headers.put("anthropic-beta", beta.trim());
        }
        return Map.copyOf(headers);
    }

    private static void requireSuccess(String body) {
        var base = tree(body).path("base_resp");
        if (!base.isObject()) {
            return;
        }
        var code = base.path("status_code").asInt(0);
        if (code == 0) {
            return;
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "ai-provider-request-failed",
                "供应商接口返回错误："
                        + base.path("status_msg").asText("")
                        + " ("
                        + code
                        + ")。");
    }

    private static JsonNode tree(String body) {
        try {
            return JsonUtils.mapper().readTree(body);
        } catch (Exception error) {
            throw AiProviderHttp.invalidRequestResponse(error);
        }
    }

    private static <T> T parse(String body, Class<T> type) {
        try {
            return JsonUtils.jsonToObject(body, type);
        } catch (RuntimeException error) {
            throw AiProviderHttp.invalidRequestResponse(error);
        }
    }

    private static byte[] hex(String value) {
        try {
            return HexFormat.of().parseHex(value.trim());
        } catch (IllegalArgumentException error) {
            throw AiProviderHttp.invalidRequestResponse(error);
        }
    }

    private static String contentType(String format) {
        return switch (format) {
            case "mp3" -> "audio/mpeg";
            case "pcm" -> "audio/pcm";
            case "flac" -> "audio/flac";
            case "wav", "pcmu_wav" -> "audio/wav";
            case "opus" -> "audio/opus";
            default -> "application/octet-stream";
        };
    }

    private Duration timeout(AiChannelEntity channel) {
        return channel.getTimeoutMs() == null || channel.getTimeoutMs() <= 0
                ? http.responseTimeout()
                : Duration.ofMillis(channel.getTimeoutMs());
    }

    private static String requiredText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw invalid(label + "不能为空。");
        }
        return value.trim();
    }

    private static String requiredChoice(String value, Set<String> choices, String label) {
        if (!StringUtils.hasText(value)) {
            throw invalid(label + "不能为空。");
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!choices.contains(normalized)) {
            throw invalid(label + "不受支持：" + value + "。");
        }
        return normalized;
    }

    private static void putIfText(Map<String, Object> body, String key, String value) {
        if (StringUtils.hasText(value)) {
            body.put(key, value.trim());
        }
    }

    private static void putIfText(UriComponentsBuilder builder, String key, String value) {
        if (StringUtils.hasText(value)) {
            builder.queryParam(key, value.trim());
        }
    }

    private static String trimmed(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int characters(String value) {
        return value.codePointCount(0, value.length());
    }

    private static int billedCharacters(String value) {
        var count = 0;
        for (var offset = 0; offset < value.length(); ) {
            var codePoint = value.codePointAt(offset);
            count += Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN ? 2 : 1;
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "ai-provider-request-invalid", message);
    }
}
