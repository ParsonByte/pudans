package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

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
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiCapabilityGateway;
import cn.tolim.pudans.core.ai.provider.AiTranscriptionOptions;
import cn.tolim.pudans.core.ai.provider.deepseek.DeepSeekGateway;
import cn.tolim.pudans.core.ai.provider.deepseek.DeepSeekProvider;
import cn.tolim.pudans.core.storage.driver.StorageIo;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiChannelCapabilityEndpoint implements CustomEndpoint {

    private static final String TAG = "AiChannelCapabilityV1alpha1";

    private static final long MAX_DOCUMENT_BYTES = 100L * 1024 * 1024;

    private static final long MAX_MEDIA_BYTES = 512L * 1024 * 1024;

    private static final long MAX_IMAGE_BYTES = 64L * 1024 * 1024;

    private static final long MAX_USER_DATA_BYTES = 32L * 1024 * 1024;

    private static final long MAX_AUDIO_CLONE_BYTES = 20L * 1024 * 1024;

    private static final long MAX_GENERATION_INPUT_BYTES = 50L * 1024 * 1024;

    private static final long MAX_TRANSCRIPTION_BYTES = 8L * 1024 * 1024;

    private static final long MAX_MINIMAX_TRANSCRIPTION_BYTES = 50L * 1024 * 1024;

    private final AiChannelResolver resolver;

    private final DeepSeekGateway deepSeekGateway;

    private final DeepSeekProvider deepSeekProvider;

    private final List<AiCapabilityGateway> gateways;

    private final PudansProperties properties;

    // 注册渠道文件、语音与文本协议能力路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aichannels/{name}/-/files", this::uploadFile, builder -> builder
                        .operationId("uploadAiChannelFile")
                        .description("Upload a file through the channel provider's Files API.")
                        .tag(TAG)
                        .requestBody(multipartRequest())
                        .response(responseBuilder().implementation(AiFile.class)))
                .GET("/aichannels/{name}/-/files", this::listFiles, builder -> builder
                        .operationId("listAiChannelFiles")
                        .description("List files through the channel provider's Files API.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiFilePage.class)))
                .GET("/aichannels/{name}/-/files/{fileId}", this::retrieveFile, builder -> builder
                        .operationId("getAiChannelFile")
                        .description("Retrieve a file through the channel provider's Files API.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiFile.class)))
                .GET("/aichannels/{name}/-/files/{fileId}/content", this::downloadFile, builder -> builder
                        .operationId("downloadAiChannelFile")
                        .description("Download file content through the channel provider's Files API.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("File content")))
                .DELETE("/aichannels/{name}/-/files/{fileId}", this::deleteFile, builder -> builder
                        .operationId("deleteAiChannelFile")
                        .description("Delete a file through the channel provider's Files API.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiFileDeletion.class)))
                .POST("/aichannels/{name}/-/anthropic-files", this::uploadAnthropicFile, builder -> builder
                        .operationId("uploadAiChannelAnthropicFile")
                        .description("Upload a file through the DeepSeek Anthropic-compatible Files API.")
                        .tag(TAG)
                        .requestBody(multipartRequest())
                        .response(responseBuilder().implementation(AiAnthropicFile.class)))
                .GET("/aichannels/{name}/-/anthropic-files", this::listAnthropicFiles, builder -> builder
                        .operationId("listAiChannelAnthropicFiles")
                        .description("List files through the DeepSeek Anthropic-compatible Files API.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiAnthropicFilePage.class)))
                .GET("/aichannels/{name}/-/anthropic-files/{fileId}", this::retrieveAnthropicFile, builder -> builder
                        .operationId("getAiChannelAnthropicFile")
                        .description("Retrieve a file through the DeepSeek Anthropic-compatible Files API.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiAnthropicFile.class)))
                .DELETE("/aichannels/{name}/-/anthropic-files/{fileId}", this::deleteAnthropicFile, builder -> builder
                        .operationId("deleteAiChannelAnthropicFile")
                        .description("Delete a file through the DeepSeek Anthropic-compatible Files API.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiAnthropicFileDeletion.class)))
                .GET("/aichannels/{name}/-/balance", this::balance, builder -> builder
                        .operationId("getAiChannelBalance")
                        .description("Get the DeepSeek account balance.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiBalance.class)))
                .POST("/aichannels/{name}/-/search", this::search, builder -> builder
                        .operationId("searchAiChannel")
                        .description("Search the web through the DeepSeek Search API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiSearchRequest.class))
                        .response(responseBuilder().implementation(AiSearchResponse.class)))
                .POST("/aichannels/{name}/-/fetch", this::fetch, builder -> builder
                        .operationId("fetchAiChannelUrl")
                        .description("Fetch a web page through the DeepSeek Fetch API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiFetchRequest.class))
                        .response(responseBuilder().implementation(AiFetchResponse.class)))
                .POST("/aichannels/{name}/-/ocr", this::ocr, builder -> builder
                        .operationId("ocrAiChannelFile")
                        .description("Recognize a document or image through the DeepSeek OCR API.")
                        .tag(TAG)
                        .requestBody(multipartRequest())
                        .response(responseBuilder().implementation(AiOcrResult.class)))
                .POST("/aichannels/{name}/-/tts", this::synthesize, builder -> builder
                        .operationId("synthesizeAiChannelSpeech")
                        .description("Synthesize speech through the channel provider's Text to Speech API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiSpeechRequest.class))
                        .response(responseBuilder().responseCode("200").description("Audio stream")))
                .POST("/aichannels/{name}/-/asr", this::transcribe, builder -> builder
                        .operationId("transcribeAiChannelAudio")
                        .description("Transcribe audio through the channel provider's Speech to Text API.")
                        .tag(TAG)
                        .requestBody(multipartRequest())
                        .response(responseBuilder().implementation(AiTranscriptionResult.class)))
                .POST("/aichannels/{name}/-/fim", this::fim, builder -> builder
                        .operationId("completeAiChannelFim")
                        .description("Create a DeepSeek FIM completion, optionally as server-sent events.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiFimRequest.class))
                        .response(responseBuilder().implementation(AiFimResult.class)))
                .POST("/aichannels/{name}/-/chat-completions", this::chatCompletions, builder -> builder
                        .operationId("createAiChannelChatCompletion")
                        .description("Call the channel provider's Chat Completions API with a raw JSON body.")
                        .tag(TAG)
                        .requestBody(jsonObjectRequest())
                        .response(responseBuilder().responseCode("200").description("Provider JSON")))
                .POST("/aichannels/{name}/-/messages", this::messages, builder -> builder
                        .operationId("createAiChannelMessage")
                        .description("Call the channel provider's Messages API with a raw JSON body.")
                        .tag(TAG)
                        .requestBody(jsonObjectRequest())
                        .response(responseBuilder().responseCode("200").description("Provider JSON")))
                .POST(
                        "/aichannels/{name}/-/anthropic/messages",
                        this::anthropicMessages,
                        builder -> builder
                        .operationId("createAiChannelAnthropicMessage")
                        .description("Call the channel provider's Anthropic Messages compatibility alias "
                                + "with a raw JSON body.")
                        .tag(TAG)
                        .requestBody(jsonObjectRequest())
                        .response(responseBuilder().responseCode("200").description("Provider JSON")))
                .POST("/aichannels/{name}/-/responses", this::responses, builder -> builder
                        .operationId("createAiChannelResponse")
                        .description("Call the channel provider's Responses API with a raw JSON body.")
                        .tag(TAG)
                        .requestBody(jsonObjectRequest())
                        .response(responseBuilder().responseCode("200").description("Provider JSON")))
                .build();
    }

    private Mono<ServerResponse> uploadFile(ServerRequest request) {
        return channel(request).flatMap(resolved -> request.multipartData()
                .flatMap(parts -> {
                    var purpose = requiredForm(parts, "purpose");
                    return readFile(parts, "file", uploadLimit(purpose, parts.getFirst("file")))
                            .flatMap(file -> {
                                var expires = expiresSeconds(parts);
                                return gateway(resolved)
                                        .uploadFile(
                                                resolved.channel(),
                                                resolved.credentials(),
                                                purpose,
                                                file.filename(),
                                                contentType(file),
                                                file.path(),
                                                expires)
                                        .flatMap(result -> ServerResponse.ok().bodyValue(result))
                                        .doFinally(signal -> StorageIo.deleteQuietly(file.path()));
                            });
                }));
    }

    private Mono<ServerResponse> listFiles(ServerRequest request) {
        return channel(request)
                .flatMap(resolved -> gateway(resolved)
                        .listFiles(
                                resolved.channel(),
                                resolved.credentials(),
                                request.queryParam("after").orElse(null),
                                queryInteger(request, "limit"),
                                request.queryParam("order").orElse(null),
                                request.queryParam("purpose").orElse(null))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> retrieveFile(ServerRequest request) {
        return channel(request)
                .flatMap(resolved -> gateway(resolved)
                        .retrieveFile(
                                resolved.channel(),
                                resolved.credentials(),
                                request.pathVariable("fileId"))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> downloadFile(ServerRequest request) {
        return channel(request)
                .flatMap(resolved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(
                                gateway(resolved)
                                        .downloadFile(
                                                resolved.channel(),
                                                resolved.credentials(),
                                                request.pathVariable("fileId")),
                                DataBuffer.class));
    }

    private Mono<ServerResponse> deleteFile(ServerRequest request) {
        return channel(request)
                .flatMap(resolved -> gateway(resolved)
                        .deleteFile(
                                resolved.channel(),
                                resolved.credentials(),
                                request.pathVariable("fileId"))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> uploadAnthropicFile(ServerRequest request) {
        return deepSeek(request).flatMap(resolved -> request.multipartData()
                .flatMap(parts -> {
                    var purpose = optionalForm(parts, "purpose");
                    return readFile(parts, "file", uploadLimit(purpose, parts.getFirst("file")))
                            .flatMap(file -> deepSeekGateway
                                    .uploadAnthropicFile(
                                            resolved.channel(),
                                            resolved.credentials(),
                                            purpose,
                                            file.filename(),
                                            contentType(file),
                                            file.path(),
                                            expiresSeconds(parts))
                                    .flatMap(result -> ServerResponse.ok().bodyValue(result))
                                    .doFinally(signal -> StorageIo.deleteQuietly(file.path())));
                }));
    }

    private Mono<ServerResponse> listAnthropicFiles(ServerRequest request) {
        return deepSeek(request)
                .flatMap(resolved -> deepSeekGateway
                        .listAnthropicFiles(
                                resolved.channel(),
                                resolved.credentials(),
                                request.queryParam("after_id").orElse(null),
                                request.queryParam("before_id").orElse(null),
                                queryInteger(request, "limit"))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> retrieveAnthropicFile(ServerRequest request) {
        return deepSeek(request)
                .flatMap(resolved -> deepSeekGateway
                        .retrieveAnthropicFile(
                                resolved.channel(),
                                resolved.credentials(),
                                request.pathVariable("fileId"))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> deleteAnthropicFile(ServerRequest request) {
        return deepSeek(request)
                .flatMap(resolved -> deepSeekGateway
                        .deleteAnthropicFile(
                                resolved.channel(),
                                resolved.credentials(),
                                request.pathVariable("fileId"))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> balance(ServerRequest request) {
        return deepSeek(request)
                .flatMap(resolved -> deepSeekGateway
                        .balance(resolved.channel(), resolved.credentials())
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> search(ServerRequest request) {
        return deepSeek(request).flatMap(resolved -> jsonBody(request, AiSearchRequest.class)
                .flatMap(body -> deepSeekGateway
                        .search(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> fetch(ServerRequest request) {
        return deepSeek(request).flatMap(resolved -> jsonBody(request, AiFetchRequest.class)
                .flatMap(body -> deepSeekGateway
                        .fetch(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> ocr(ServerRequest request) {
        return deepSeek(request).flatMap(resolved -> request.multipartData()
                .flatMap(parts -> {
                    var purpose = requiredForm(parts, "purpose");
                    return readFile(parts, "file", MAX_DOCUMENT_BYTES).flatMap(file -> deepSeekGateway
                        .ocr(
                                resolved.channel(),
                                resolved.credentials(),
                                purpose,
                                file.filename(),
                                contentType(file),
                                file.path())
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))
                        .doFinally(signal -> StorageIo.deleteQuietly(file.path())));
                }));
    }

    private Mono<ServerResponse> synthesize(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiSpeechRequest.class)
                .flatMap(body -> gateway(resolved)
                        .synthesize(resolved.channel(), resolved.credentials(), body)
                        .flatMap(speech -> ServerResponse.ok()
                                .contentType(MediaType.parseMediaType(speech.contentType()))
                                .body(speech.audio(), DataBuffer.class))));
    }

    private Mono<ServerResponse> transcribe(ServerRequest request) {
        return channel(request).flatMap(resolved -> request.multipartData()
                .flatMap(parts -> readFile(parts, "file", transcriptionLimit(resolved))
                        .flatMap(file -> gateway(resolved)
                        .transcribe(
                                resolved.channel(),
                                resolved.credentials(),
                                file.filename(),
                                contentType(file),
                                file.path(),
                                new AiTranscriptionOptions(
                                        optionalForm(parts, "model"),
                                        optionalForm(parts, "language"),
                                        optionalForm(parts, "response_format"),
                                        optionalForm(parts, "timestamp_level")))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))
                        .doFinally(signal -> StorageIo.deleteQuietly(file.path())))));
    }

    private Mono<ServerResponse> fim(ServerRequest request) {
        return deepSeek(request).flatMap(resolved -> jsonBody(request, AiFimRequest.class)
                .flatMap(body -> Boolean.TRUE.equals(body.stream())
                        ? ServerResponse.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .body(
                                        deepSeekGateway.fimStream(
                                                resolved.channel(), resolved.credentials(), body),
                                        new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                        : deepSeekGateway
                                .fim(resolved.channel(), resolved.credentials(), body)
                                .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> chatCompletions(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonMap(request).flatMap(body -> stream(body)
                ? ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(
                                gateway(resolved).chatCompletionsStream(
                                        resolved.channel(), resolved.credentials(), body),
                                new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                : gateway(resolved)
                        .chatCompletions(resolved.channel(), resolved.credentials(), body)
                        .flatMap(AiProviderRawJson::response)));
    }

    private Mono<ServerResponse> messages(ServerRequest request) {
        return messages(request, true);
    }

    private Mono<ServerResponse> anthropicMessages(ServerRequest request) {
        return messages(request, false);
    }

    private Mono<ServerResponse> messages(ServerRequest request, boolean versioned) {
        var beta = request.headers().firstHeader("anthropic-beta");
        return channel(request).flatMap(resolved -> jsonMap(request).flatMap(body -> stream(body)
                ? ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(
                                gateway(resolved).messagesStream(
                                        resolved.channel(), resolved.credentials(), body, beta, versioned),
                                new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                : gateway(resolved)
                        .messages(resolved.channel(), resolved.credentials(), body, beta, versioned)
                        .flatMap(AiProviderRawJson::response)));
    }

    private Mono<ServerResponse> responses(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonMap(request).flatMap(body -> stream(body)
                ? ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(
                                gateway(resolved).responsesStream(
                                        resolved.channel(), resolved.credentials(), body),
                                new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                : gateway(resolved)
                        .responses(resolved.channel(), resolved.credentials(), body)
                        .flatMap(AiProviderRawJson::response)));
    }

    private Mono<AiChannels.ResolvedChannel> channel(ServerRequest request) {
        return resolver.require(request);
    }

    private Mono<AiChannels.ResolvedChannel> deepSeek(ServerRequest request) {
        return resolver.require(request, deepSeekProvider.descriptor().provider());
    }

    private AiCapabilityGateway gateway(AiChannels.ResolvedChannel resolved) {
        for (var gateway : gateways) {
            if (gateway.provider().equals(resolved.channel().getProvider())) {
                return gateway;
            }
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "ai-channel-capability-unsupported",
                "当前渠道不支持该能力。");
    }

    private Mono<UploadFile> readFile(
            MultiValueMap<String, Part> parts, String name, long maxBytes) {
        var part = parts.getFirst(name);
        if (!(part instanceof FilePart file)) {
            return Mono.error(invalid("需要名为 " + name + " 的 multipart 文件字段。"));
        }
        var declaredLength = file.headers().getContentLength();
        if (declaredLength > maxBytes) {
            return Mono.error(fileTooLarge(maxBytes));
        }
        return StorageIo.spool(file.content(), uploadDirectory(), maxBytes)
                .onErrorMap(
                        ApiException.class,
                        error -> "storage-file-too-large".equals(error.getCode())
                                ? fileTooLarge(maxBytes)
                                : error)
                .flatMap(path -> {
                    var upload = new UploadFile(
                            file.filename(), file.headers().getContentType(), path);
                    return file.delete()
                            .onErrorResume(error -> Mono.empty())
                            .thenReturn(upload);
                })
                .switchIfEmpty(Mono.error(invalid("上传文件不能为空。")));
    }

    private static String contentType(UploadFile file) {
        return file.contentType() == null ? null : file.contentType().toString();
    }

    private static String requiredForm(MultiValueMap<String, Part> parts, String name) {
        var value = optionalForm(parts, name);
        if (!StringUtils.hasText(value)) {
            throw invalid("需要名为 " + name + " 的 multipart 文本字段。");
        }
        return value;
    }

    private static String optionalForm(MultiValueMap<String, Part> parts, String name) {
        var part = parts.getFirst(name);
        if (part == null) {
            return null;
        }
        if (!(part instanceof FormFieldPart field)) {
            throw invalid("字段 " + name + " 必须是 multipart 文本字段。");
        }
        return field.value();
    }

    private static Long expiresSeconds(MultiValueMap<String, Part> parts) {
        var anchor = optionalForm(parts, "expires_after[anchor]");
        var value = optionalForm(parts, "expires_after[seconds]");
        if (value == null) {
            value = optionalForm(parts, "expiresAfterSeconds");
        }
        if (anchor == null && value == null) {
            return null;
        }
        if (anchor != null && !"created_at".equals(anchor)) {
            throw invalid("expires_after[anchor] 只能是 created_at。");
        }
        if (anchor == null || value == null) {
            throw invalid("expires_after[anchor] 与 expires_after[seconds] 必须同时提供。");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            throw invalid("expires_after[seconds] 必须是整数。");
        }
    }

    private static Integer queryInteger(ServerRequest request, String name) {
        var value = request.queryParam(name).orElse(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw invalid(name + " 必须是整数。");
        }
    }

    private static <T> Mono<T> jsonBody(ServerRequest request, Class<T> type) {
        return request.bodyToMono(type).switchIfEmpty(Mono.error(invalid("请求体不能为空。")));
    }

    private static Mono<Map<String, Object>> jsonMap(ServerRequest request) {
        return request
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")));
    }

    private static boolean stream(Map<String, Object> body) {
        return Boolean.TRUE.equals(body.get("stream"));
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ai-provider-request-invalid", message);
    }

    private static ApiException fileTooLarge(long maxBytes) {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "ai-provider-file-too-large",
                "上传文件不能超过 " + maxBytes / (1024 * 1024) + " MiB。");
    }

    private static long transcriptionLimit(AiChannels.ResolvedChannel resolved) {
        return "minimax".equals(resolved.channel().getProvider())
                ? MAX_MINIMAX_TRANSCRIPTION_BYTES
                : MAX_TRANSCRIPTION_BYTES;
    }

    private static long uploadLimit(String purpose, Part part) {
        var normalized = purpose == null ? "" : purpose.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "text_recognition", "rich_layout_recognition", "t2a_async_input" ->
                    MAX_DOCUMENT_BYTES;
            case "voice_clone", "prompt_audio" -> MAX_AUDIO_CLONE_BYTES;
            case "video_understanding" -> MAX_MEDIA_BYTES;
            case "video_generation_input" -> MAX_GENERATION_INPUT_BYTES;
            case "image_generation_input" -> MAX_IMAGE_BYTES;
            default -> image(part) ? MAX_IMAGE_BYTES : MAX_USER_DATA_BYTES;
        };
    }

    private static boolean image(Part part) {
        if (!(part instanceof FilePart file)) {
            return false;
        }
        var contentType = file.headers().getContentType();
        if (contentType != null && "image".equalsIgnoreCase(contentType.getType())) {
            return true;
        }
        var filename = file.filename().toLowerCase(java.util.Locale.ROOT);
        return filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".png")
                || filename.endsWith(".gif")
                || filename.endsWith(".webp")
                || filename.endsWith(".heic")
                || filename.endsWith(".heif");
    }

    private Path uploadDirectory() {
        return properties.getWorkDir().toAbsolutePath().normalize().resolve("ai/.tmp");
    }

    private static org.springdoc.core.fn.builders.requestbody.Builder multipartRequest() {
        return requestBodyBuilder()
                .required(true)
                .content(contentBuilder().mediaType(MediaType.MULTIPART_FORM_DATA_VALUE));
    }

    private static <T> org.springdoc.core.fn.builders.requestbody.Builder jsonRequest(Class<T> type) {
        return requestBodyBuilder()
                .required(true)
                .content(contentBuilder()
                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(schemaBuilder().implementation(type)));
    }

    private static org.springdoc.core.fn.builders.requestbody.Builder jsonObjectRequest() {
        return requestBodyBuilder()
                .required(true)
                .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE));
    }

    private record UploadFile(String filename, MediaType contentType, Path path) {}

}
