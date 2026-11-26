package cn.tolim.pudans.core.ai.provider.deepseek;

import cn.tolim.pudans.contract.ai.dto.AiAnthropicFile;
import cn.tolim.pudans.contract.ai.dto.AiAnthropicFileDeletion;
import cn.tolim.pudans.contract.ai.dto.AiAnthropicFilePage;
import cn.tolim.pudans.contract.ai.dto.AiBalance;
import cn.tolim.pudans.contract.ai.dto.AiFetchRequest;
import cn.tolim.pudans.contract.ai.dto.AiFetchResponse;
import cn.tolim.pudans.contract.ai.dto.AiFile;
import cn.tolim.pudans.contract.ai.dto.AiFileDeletion;
import cn.tolim.pudans.contract.ai.dto.AiFilePage;
import cn.tolim.pudans.contract.ai.dto.AiFimRequest;
import cn.tolim.pudans.contract.ai.dto.AiFimResult;
import cn.tolim.pudans.contract.ai.dto.AiOcrResult;
import cn.tolim.pudans.contract.ai.dto.AiSearchRequest;
import cn.tolim.pudans.contract.ai.dto.AiSearchResponse;
import cn.tolim.pudans.contract.ai.dto.AiSpeechRequest;
import cn.tolim.pudans.contract.ai.dto.AiTranscriptionResult;
import cn.tolim.pudans.core.ai.provider.AiProviderAuth;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekGateway {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String ANTHROPIC_FILES_BETA = "files-api-2025-04-14";

    private static final Set<String> FILE_PURPOSES =
            Set.of("user_data", "text_recognition", "rich_layout_recognition");

    private static final Set<String> OCR_PURPOSES =
            Set.of("text_recognition", "rich_layout_recognition");

    private static final Set<String> TTS_VOICES = Set.of("stella", "tide");

    private static final Set<String> TTS_FORMATS = Set.of("wav", "pcm");

    private static final Set<String> FILE_ORDERS = Set.of("asc", "desc");

    private static final int MAX_TTS_BODY_BYTES = 64 * 1024;

    private static final int FILE_LIMIT_MIN = 1;

    private static final int FILE_LIMIT_MAX = 1000;

    private static final int ANTHROPIC_FILE_LIMIT_DEFAULT = 20;

    private static final long EXPIRES_MIN_SECONDS = 3600;

    private static final long EXPIRES_MAX_SECONDS = 2_592_000;

    private final AiProviderHttp http;

    private final DeepSeekProvider provider;

    public DeepSeekGateway(AiProviderHttp http, DeepSeekProvider provider) {
        this.http = http;
        this.provider = provider;
    }

    public Mono<AiFile> uploadFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String purpose,
            String filename,
            String contentType,
            Path content,
            Long expiresAfterSeconds) {
        var normalizedPurpose = requiredChoice(purpose, FILE_PURPOSES, "文件用途");
        var normalizedFilename = filename(filename);
        if (empty(content)) {
            return Mono.error(invalid("上传文件不能为空。"));
        }
        validateExpiry(expiresAfterSeconds);
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("purpose", normalizedPurpose);
        addExpiry(parts, expiresAfterSeconds);
        parts.add("file", filePart(normalizedFilename, contentType, content));
        return http.postMultipart(
                        URI.create(provider.filesUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        parts,
                        timeout(channel))
                .map(body -> parse(body, AiFile.class));
    }

    public Mono<AiFilePage> listFiles(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String after,
            Integer limit,
            String order,
            String purpose) {
        var normalizedLimit = bounded(limit, FILE_LIMIT_MAX, FILE_LIMIT_MIN, FILE_LIMIT_MAX);
        var normalizedOrder = order == null
                ? "asc"
                : requiredChoice(order, FILE_ORDERS, "文件排序");
        var normalizedPurpose = purpose == null
                ? null
                : requiredChoice(purpose, FILE_PURPOSES, "文件用途");
        var builder = UriComponentsBuilder.fromUriString(provider.filesUrl(channel.getBaseUrl()))
                .queryParam("limit", normalizedLimit)
                .queryParam("order", normalizedOrder);
        if (StringUtils.hasText(after)) {
            builder.queryParam("after", after.trim());
        }
        if (normalizedPurpose != null) {
            builder.queryParam("purpose", normalizedPurpose);
        }
        return http.getJson(
                        builder.build().encode().toUri(),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(body -> parse(body, AiFilePage.class));
    }

    public Mono<AiFile> retrieveFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String fileId) {
        return http.getJson(
                        fileUri(channel, fileId), credentials, Map.of(), timeout(channel))
                .map(body -> parse(body, AiFile.class));
    }

    public Mono<AiFileDeletion> deleteFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String fileId) {
        return http.deleteJson(
                        fileUri(channel, fileId), credentials, Map.of(), timeout(channel))
                .map(body -> parse(body, AiFileDeletion.class));
    }

    public Mono<AiAnthropicFile> uploadAnthropicFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String purpose,
            String filename,
            String contentType,
            Path content,
            Long expiresAfterSeconds) {
        var normalizedPurpose = purpose == null
                ? null
                : requiredChoice(purpose, FILE_PURPOSES, "文件用途");
        var normalizedFilename = filename(filename);
        if (empty(content)) {
            return Mono.error(invalid("上传文件不能为空。"));
        }
        validateExpiry(expiresAfterSeconds);
        var parts = new LinkedMultiValueMap<String, Object>();
        if (normalizedPurpose != null) {
            parts.add("purpose", normalizedPurpose);
        }
        addExpiry(parts, expiresAfterSeconds);
        parts.add("file", filePart(normalizedFilename, contentType, content));
        return http.postMultipart(
                        URI.create(anthropicFilesUrl(channel)),
                        credentials,
                        anthropicFilesHeaders(),
                        parts,
                        timeout(channel),
                        AiProviderAuth.X_API_KEY)
                .map(body -> parse(body, AiAnthropicFile.class));
    }

    public Mono<AiAnthropicFilePage> listAnthropicFiles(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String afterId,
            String beforeId,
            Integer limit) {
        if (StringUtils.hasText(afterId) && StringUtils.hasText(beforeId)) {
            return Mono.error(invalid("after_id 和 before_id 不能同时使用。"));
        }
        var normalizedLimit =
                bounded(limit, ANTHROPIC_FILE_LIMIT_DEFAULT, FILE_LIMIT_MIN, FILE_LIMIT_MAX);
        var builder = UriComponentsBuilder.fromUriString(anthropicFilesUrl(channel))
                .queryParam("limit", normalizedLimit);
        if (StringUtils.hasText(afterId)) {
            builder.queryParam("after_id", afterId.trim());
        }
        if (StringUtils.hasText(beforeId)) {
            builder.queryParam("before_id", beforeId.trim());
        }
        return http.getJson(
                        builder.build().encode().toUri(),
                        credentials,
                        anthropicFilesHeaders(),
                        timeout(channel),
                        AiProviderAuth.X_API_KEY)
                .map(body -> parse(body, AiAnthropicFilePage.class));
    }

    public Mono<AiAnthropicFile> retrieveAnthropicFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String fileId) {
        return http.getJson(
                        anthropicFileUri(channel, fileId),
                        credentials,
                        anthropicFilesHeaders(),
                        timeout(channel),
                        AiProviderAuth.X_API_KEY)
                .map(body -> parse(body, AiAnthropicFile.class));
    }

    public Mono<AiAnthropicFileDeletion> deleteAnthropicFile(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String fileId) {
        return http.deleteJson(
                        anthropicFileUri(channel, fileId),
                        credentials,
                        anthropicFilesHeaders(),
                        timeout(channel),
                        AiProviderAuth.X_API_KEY)
                .map(body -> parse(body, AiAnthropicFileDeletion.class));
    }

    public Mono<AiBalance> balance(
            AiChannelEntity channel, Map<String, String> credentials) {
        return http.getJson(
                        URI.create(provider.balanceUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        timeout(channel))
                .map(body -> parse(body, AiBalance.class));
    }

    public Mono<AiSearchResponse> search(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            return Mono.error(invalid("搜索内容不能为空。"));
        }
        var query = request.query().trim();
        if (characters(query) > 128) {
            return Mono.error(invalid("搜索内容不能超过 128 个字符。"));
        }
        var count = bounded(request.count(), 10, 1, 50);
        var originalQuestion = trimmed(request.originalQuestion());
        if (originalQuestion != null && characters(originalQuestion) > 128) {
            return Mono.error(invalid("原始问题不能超过 128 个字符。"));
        }
        var localization = trimmed(request.localization());
        var upstream = new AiSearchRequest(
                query,
                count,
                originalQuestion,
                localization,
                Boolean.TRUE.equals(request.needContent()));
        return http.postJson(
                        URI.create(provider.toolsUrl(channel.getBaseUrl(), "search")),
                        credentials,
                        Map.of(),
                        upstream,
                        timeout(channel))
                .map(body -> parse(body, AiSearchResponse.class));
    }

    public Mono<AiFetchResponse> fetch(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiFetchRequest request) {
        var url = request == null ? null : trimmed(request.url());
        if (url == null || url.length() > 8192) {
            return Mono.error(invalid("网页地址不能为空且不能超过 8192 个字符。"));
        }
        validateHttpUrl(url);
        return http.postJson(
                        URI.create(provider.toolsUrl(channel.getBaseUrl(), "fetch")),
                        credentials,
                        Map.of(),
                        Map.of("url", url),
                        timeout(channel))
                .map(body -> parse(body, AiFetchResponse.class));
    }

    public Mono<AiOcrResult> ocr(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String purpose,
            String filename,
            String contentType,
            Path content) {
        var normalizedPurpose = requiredChoice(purpose, OCR_PURPOSES, "OCR 识别方式");
        if (empty(content)) {
            return Mono.error(invalid("OCR 文件不能为空。"));
        }
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", filePart(filename(filename), contentType, content));
        parts.add("purpose", normalizedPurpose);
        return http.postMultipart(
                        URI.create(provider.toolsUrl(channel.getBaseUrl(), "ocr")),
                        credentials,
                        Map.of(),
                        parts,
                        timeout(channel))
                .map(DeepSeekGateway::ocrResult);
    }

    public Mono<DeepSeekSpeech> synthesize(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiSpeechRequest request) {
        if (request == null || !StringUtils.hasText(request.text())) {
            return Mono.error(invalid("语音合成文本不能为空。"));
        }
        if (billedCharacters(request.text()) > 10_000) {
            return Mono.error(invalid("语音合成文本不能超过 10000 个计费字符。"));
        }
        var voice = request.voiceId() == null || !StringUtils.hasText(request.voiceId())
                ? "stella"
                : requiredChoice(request.voiceId(), TTS_VOICES, "语音音色");
        var format = request.format() == null || !StringUtils.hasText(request.format())
                ? "wav"
                : requiredChoice(request.format(), TTS_FORMATS, "音频格式");
        var body = new LinkedHashMap<String, Object>();
        body.put("text", request.text());
        body.put("voice_id", voice);
        body.put("format", format);
        body.put(
                "filter_markdown",
                request.filterMarkdown() == null || request.filterMarkdown());
        if (jsonBytes(body) > MAX_TTS_BODY_BYTES) {
            return Mono.error(invalid("语音合成请求体不能超过 64 KiB。"));
        }
        return Mono.just(new DeepSeekSpeech(
                "pcm".equals(format) ? "audio/pcm" : "audio/wav",
                http.postBinaryJsonStream(
                        URI.create(provider.toolsUrl(channel.getBaseUrl(), "tts")),
                        credentials,
                        Map.of(),
                        body,
                        timeout(channel))));
    }

    public Mono<AiTranscriptionResult> transcribe(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String filename,
            String contentType,
            Path content) {
        if (empty(content)) {
            return Mono.error(invalid("音频文件不能为空。"));
        }
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", filePart(filename(filename), contentType, content));
        return http.postMultipart(
                        URI.create(provider.toolsUrl(channel.getBaseUrl(), "asr")),
                        credentials,
                        Map.of(),
                        parts,
                        timeout(channel))
                .map(body -> parse(body, AiTranscriptionResult.class));
    }

    public Mono<AiFimResult> fim(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiFimRequest request) {
        return validateFim(request)
                .flatMap(body -> http.postJson(
                        URI.create(provider.fimUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        body,
                        timeout(channel)))
                .map(body -> parse(body, AiFimResult.class));
    }

    public Flux<ServerSentEvent<String>> fimStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            AiFimRequest request) {
        return validateFim(request)
                .flatMapMany(body -> http.postSse(
                        URI.create(provider.fimUrl(channel.getBaseUrl())),
                        credentials,
                        Map.of(),
                        body,
                        timeout(channel)));
    }

    public Mono<String> chatCompletions(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body) {
        return http.postJson(
                URI.create(provider.chatCompletionsUrl(channel)),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    public Flux<ServerSentEvent<String>> chatCompletionsStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body) {
        return http.postSse(
                URI.create(provider.chatCompletionsUrl(channel)),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    public Mono<String> messages(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta) {
        return messages(channel, credentials, body, beta, true);
    }

    public Mono<String> messages(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta,
            boolean versioned) {
        return http.postJson(
                URI.create(provider.messagesUrl(channel.getBaseUrl(), versioned)),
                credentials,
                messagesHeaders(beta),
                body,
                timeout(channel),
                AiProviderAuth.X_API_KEY);
    }

    public Flux<ServerSentEvent<String>> messagesStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta) {
        return messagesStream(channel, credentials, body, beta, true);
    }

    public Flux<ServerSentEvent<String>> messagesStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body,
            String beta,
            boolean versioned) {
        return http.postSse(
                URI.create(provider.messagesUrl(channel.getBaseUrl(), versioned)),
                credentials,
                messagesHeaders(beta),
                body,
                timeout(channel),
                AiProviderAuth.X_API_KEY);
    }

    public Mono<String> responses(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body) {
        return http.postJson(
                URI.create(provider.responsesUrl(channel.getBaseUrl())),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    public Flux<ServerSentEvent<String>> responsesStream(
            AiChannelEntity channel,
            Map<String, String> credentials,
            Map<String, Object> body) {
        return http.postSse(
                URI.create(provider.responsesUrl(channel.getBaseUrl())),
                credentials,
                Map.of(),
                body,
                timeout(channel));
    }

    private Mono<AiFimRequest> validateFim(AiFimRequest request) {
        if (request == null
                || !StringUtils.hasText(request.model())
                || !StringUtils.hasText(request.prompt())) {
            return Mono.error(invalid("FIM 模型和提示词不能为空。"));
        }
        if (request.logprobs() != null && (request.logprobs() < 0 || request.logprobs() > 20)) {
            return Mono.error(invalid("FIM logprobs 必须在 0 到 20 之间。"));
        }
        if (request.maxTokens() != null && request.maxTokens() < 0) {
            return Mono.error(invalid("FIM max_tokens 不能为负数。"));
        }
        if (request.temperature() != null
                && (request.temperature() < 0 || request.temperature() > 2)) {
            return Mono.error(invalid("FIM temperature 必须在 0 到 2 之间。"));
        }
        if (request.topP() != null && (request.topP() <= 0 || request.topP() > 1)) {
            return Mono.error(invalid("FIM top_p 必须大于 0 且不超过 1。"));
        }
        if (Boolean.TRUE.equals(request.echo())
                && (StringUtils.hasText(request.suffix()) || request.logprobs() != null)) {
            return Mono.error(invalid("FIM echo 不能与 suffix 或 logprobs 同时使用。"));
        }
        if (request.streamOptions() != null && !Boolean.TRUE.equals(request.stream())) {
            return Mono.error(invalid("FIM stream_options 必须与 stream=true 同时使用。"));
        }
        validateStop(request.stop());
        var streamOptions = streamOptions(request.streamOptions());
        return Mono.just(new AiFimRequest(
                request.model().trim(),
                request.prompt(),
                request.echo(),
                request.logprobs(),
                request.maxTokens(),
                request.stop(),
                request.stream(),
                streamOptions,
                request.suffix(),
                request.temperature(),
                request.topP()));
    }

    private static void validateStop(Object stop) {
        if (stop == null || stop instanceof String) {
            return;
        }
        if (!(stop instanceof List<?> values) || values.isEmpty() || values.size() > 16) {
            throw invalid("FIM stop 必须是字符串或 1 到 16 个字符串的数组。");
        }
        for (var value : values) {
            if (!(value instanceof String)) {
                throw invalid("FIM stop 数组只能包含字符串。");
            }
        }
    }

    private static Map<String, Object> streamOptions(Map<String, Object> options) {
        if (options == null) {
            return null;
        }
        var includeUsage = options.get("include_usage");
        if (includeUsage != null && !(includeUsage instanceof Boolean)) {
            throw invalid("FIM stream_options.include_usage 必须是布尔值。");
        }
        return includeUsage == null ? Map.of() : Map.of("include_usage", includeUsage);
    }

    private static void validateExpiry(Long expiresAfterSeconds) {
        if (expiresAfterSeconds != null
                && (expiresAfterSeconds < EXPIRES_MIN_SECONDS
                        || expiresAfterSeconds > EXPIRES_MAX_SECONDS)) {
            throw invalid("文件有效期必须在 3600 到 2592000 秒之间。");
        }
    }

    private static void addExpiry(
            MultiValueMap<String, Object> parts, Long expiresAfterSeconds) {
        if (expiresAfterSeconds == null) {
            return;
        }
        parts.add("expires_after[anchor]", "created_at");
        parts.add("expires_after[seconds]", expiresAfterSeconds.toString());
    }

    private String anthropicFilesUrl(AiChannelEntity channel) {
        return provider.anthropicBaseUrl(channel.getBaseUrl()) + "/files";
    }

    private URI fileUri(AiChannelEntity channel, String fileId) {
        return fileUri(provider.filesUrl(channel.getBaseUrl()), fileId);
    }

    private URI anthropicFileUri(AiChannelEntity channel, String fileId) {
        return fileUri(anthropicFilesUrl(channel), fileId);
    }

    private static URI fileUri(String baseUrl, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw invalid("文件标识不能为空。");
        }
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(fileId.trim())
                .build()
                .encode()
                .toUri();
    }

    private static Map<String, String> anthropicFilesHeaders() {
        return Map.of("anthropic-beta", ANTHROPIC_FILES_BETA);
    }

    private static Map<String, String> messagesHeaders(String beta) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        if (StringUtils.hasText(beta)) {
            headers.put("anthropic-beta", beta.trim());
        }
        return Map.copyOf(headers);
    }

    private static HttpEntity<FileSystemResource> filePart(
            String filename, String contentType, Path content) {
        var resource = new FileSystemResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        var headers = new HttpHeaders();
        headers.setContentType(contentType(contentType));
        return new HttpEntity<>(resource, headers);
    }

    private static boolean empty(Path content) {
        if (content == null) {
            return true;
        }
        try {
            return !Files.isRegularFile(content) || Files.size(content) == 0;
        } catch (IOException error) {
            return true;
        }
    }

    private static int jsonBytes(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsBytes(value).length;
        } catch (Exception error) {
            throw invalid("语音合成请求无法序列化。");
        }
    }

    private static MediaType contentType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String filename(String value) {
        var filename = StringUtils.hasText(value) ? value.trim() : "file";
        if (filename.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw invalid("文件名不能超过 512 字节。");
        }
        return filename;
    }

    private Duration timeout(AiChannelEntity channel) {
        return channel.getTimeoutMs() == null || channel.getTimeoutMs() <= 0
                ? http.responseTimeout()
                : Duration.ofMillis(channel.getTimeoutMs());
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

    private static int bounded(Integer value, int defaultValue, int min, int max) {
        var resolved = value == null ? defaultValue : value;
        if (resolved < min || resolved > max) {
            throw invalid("参数值必须在 " + min + " 到 " + max + " 之间。");
        }
        return resolved;
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

    private static String trimmed(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void validateHttpUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw invalid("网页地址必须是有效的 HTTP 或 HTTPS 地址。");
        }
        var scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(uri.getHost())
                || uri.getRawUserInfo() != null) {
            throw invalid("网页地址必须是有效的 HTTP 或 HTTPS 地址。");
        }
    }

    private static AiOcrResult ocrResult(String body) {
        try {
            var error = JsonUtils.mapper().readTree(body).path("error");
            if (!error.isMissingNode()) {
                var message = error.path("message").asText("OCR 识别失败。");
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY, "ai-provider-request-failed", message);
            }
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw AiProviderHttp.invalidRequestResponse(error);
        }
        return parse(body, AiOcrResult.class);
    }

    private static <T> T parse(String body, Class<T> type) {
        try {
            return JsonUtils.jsonToObject(body, type);
        } catch (RuntimeException error) {
            throw AiProviderHttp.invalidRequestResponse(error);
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ai-provider-request-invalid", message);
    }
}
