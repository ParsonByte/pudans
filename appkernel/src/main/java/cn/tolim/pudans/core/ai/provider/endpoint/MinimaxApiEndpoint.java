package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxApiClient;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxProtocol;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxProviderAccess;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MinimaxApiEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private static final String CODE_INVALID = "minimax-request-invalid";

    private static final String CODE_FILE_NOT_FOUND = "minimax-file-not-found";

    private static final String CODE_TOO_LARGE = "minimax-file-too-large";

    private static final int MAX_FILE_BYTES = 100 * 1024 * 1024;

    private static final int MAX_ASR_BYTES = 50 * 1024 * 1024;

    private final MinimaxProviderAccess access;

    private final MinimaxApiClient client;

    private final StorageTransfer storageTransfer;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiproviders/{name}/minimax/files", this::uploadFile,
                        builder -> jsonOperation(builder, "minimaxUploadFile", "Upload a stored file."))
                .GET("/aiproviders/{name}/minimax/files/list",
                        request -> getJson(request, MinimaxProtocol.FILES_LIST_PATH),
                        builder -> jsonOperation(builder, "minimaxListFiles", "List provider files."))
                .GET("/aiproviders/{name}/minimax/files/retrieve",
                        request -> getJson(request, MinimaxProtocol.FILES_RETRIEVE_PATH, fileId(request)),
                        builder -> jsonOperation(builder, "minimaxRetrieveFile", "Retrieve a file."))
                .GET("/aiproviders/{name}/minimax/files/content", this::fileContent,
                        builder -> jsonOperation(builder, "minimaxFileContent", "Download file content."))
                .POST("/aiproviders/{name}/minimax/files/delete", this::deleteFile,
                        builder -> jsonOperation(builder, "minimaxDeleteFile", "Delete a file."))
                .GET("/aiproviders/{name}/minimax/models",
                        request -> getJson(request, MinimaxProtocol.MODELS_PATH),
                        builder -> jsonOperation(builder, "minimaxListModels", "List OpenAI-compatible models."))
                .GET("/aiproviders/{name}/minimax/models/{modelId}",
                        request -> getJson(request, MinimaxProtocol.MODELS_PATH + "/"
                                + request.pathVariable("modelId")),
                        builder -> jsonOperation(builder, "minimaxRetrieveModel", "Retrieve a model."))
                .GET("/aiproviders/{name}/minimax/anthropic/models",
                        request -> getJson(request, MinimaxProtocol.ANTHROPIC_MODELS_PATH),
                        builder -> jsonOperation(
                                builder, "minimaxListAnthropicModels", "List Anthropic-compatible models."))
                .GET("/aiproviders/{name}/minimax/anthropic/models/{modelId}",
                        request -> getJson(request, MinimaxProtocol.ANTHROPIC_MODELS_PATH + "/"
                                + request.pathVariable("modelId")),
                        builder -> jsonOperation(
                                builder, "minimaxRetrieveAnthropicModel", "Retrieve an Anthropic model."))
                .POST("/aiproviders/{name}/minimax/messages/count-tokens",
                        request -> postJson(request, MinimaxProtocol.COUNT_TOKENS_PATH),
                        builder -> jsonOperation(
                                builder, "minimaxCountMessageTokens", "Count message input tokens."))
                .POST("/aiproviders/{name}/minimax/responses/input-tokens",
                        request -> postJson(request, MinimaxProtocol.INPUT_TOKENS_PATH),
                        builder -> jsonOperation(
                                builder, "minimaxEstimateInputTokens", "Estimate response input tokens."))
                .POST("/aiproviders/{name}/minimax/text/chatcompletion",
                        request -> postOrStream(request, MinimaxProtocol.NATIVE_CHAT_PATH),
                        builder -> jsonOperation(builder, "minimaxNativeChat", "Native text completion."))
                .POST("/aiproviders/{name}/minimax/speech/asr", this::asr,
                        builder -> jsonOperation(builder, "minimaxSpeechToText", "Transcribe audio."))
                .POST("/aiproviders/{name}/minimax/speech/t2a",
                        request -> postOrStream(request, "/v1/t2a_v2"),
                        builder -> jsonOperation(builder, "minimaxTextToSpeech", "Synthesize speech."))
                .POST("/aiproviders/{name}/minimax/speech/t2a/async",
                        request -> postJson(request, "/v1/t2a_async_v2"),
                        builder -> jsonOperation(builder, "minimaxCreateTtsTask", "Create async TTS task."))
                .GET("/aiproviders/{name}/minimax/speech/t2a/async/{taskId}",
                        request -> getJson(request, "/v1/query/t2a_async_query_v2",
                                Map.of("task_id", request.pathVariable("taskId"))),
                        builder -> jsonOperation(builder, "minimaxQueryTtsTask", "Query async TTS task."))
                .POST("/aiproviders/{name}/minimax/speech/voice/clone",
                        request -> postJson(request, "/v1/voice_clone"),
                        builder -> jsonOperation(builder, "minimaxCloneVoice", "Clone a voice."))
                .POST("/aiproviders/{name}/minimax/speech/voice/design",
                        request -> postJson(request, "/v1/voice_design"),
                        builder -> jsonOperation(builder, "minimaxDesignVoice", "Design a voice."))
                .POST("/aiproviders/{name}/minimax/speech/voice/list",
                        request -> postJson(request, "/v1/get_voice"),
                        builder -> jsonOperation(builder, "minimaxListVoices", "List voices."))
                .POST("/aiproviders/{name}/minimax/speech/voice/delete",
                        request -> postJson(request, "/v1/delete_voice"),
                        builder -> jsonOperation(builder, "minimaxDeleteVoice", "Delete a voice."))
                .POST("/aiproviders/{name}/minimax/images/generations",
                        request -> postOrStream(request, "/v1/images/generations"),
                        builder -> jsonOperation(builder, "minimaxGenerateImages", "Generate images."))
                .POST("/aiproviders/{name}/minimax/images/edits",
                        request -> postOrStream(request, "/v1/images/edits"),
                        builder -> jsonOperation(builder, "minimaxEditImages", "Edit images."))
                .POST("/aiproviders/{name}/minimax/images/context-ir",
                        request -> postJson(request, "/v1/h3_image_context_ir"),
                        builder -> jsonOperation(builder, "minimaxImageContextIr", "Enhance image prompt."))
                .POST("/aiproviders/{name}/minimax/images/legacy",
                        request -> postJson(request, "/v1/image_generation"),
                        builder -> jsonOperation(builder, "minimaxLegacyImageGeneration", "Legacy image generation."))
                .POST("/aiproviders/{name}/minimax/video/v2",
                        request -> postJson(request, "/v2/video_generation"),
                        builder -> jsonOperation(builder, "minimaxCreateVideoV2", "Create video generation task."))
                .GET("/aiproviders/{name}/minimax/video/v2",
                        request -> getJson(request, "/v2/query/video_generation"),
                        builder -> jsonOperation(builder, "minimaxListVideoV2", "List video generation tasks."))
                .GET("/aiproviders/{name}/minimax/video/v2/{taskId}",
                        request -> getJson(request, "/v2/query/video_generation/"
                                + request.pathVariable("taskId")),
                        builder -> jsonOperation(builder, "minimaxQueryVideoV2", "Query video generation task."))
                .DELETE("/aiproviders/{name}/minimax/video/v2/{taskId}",
                        request -> deleteJson(request, "/v2/video_generation/"
                                + request.pathVariable("taskId")),
                        builder -> jsonOperation(builder, "minimaxDeleteVideoV2", "Delete video task."))
                .POST("/aiproviders/{name}/minimax/video/v2/context-ir",
                        request -> postJson(request, "/v2/h3_context_ir"),
                        builder -> jsonOperation(builder, "minimaxVideoContextIr", "Enhance video prompt."))
                .POST("/aiproviders/{name}/minimax/video/v2/regenerations",
                        request -> postJson(request, "/v2/video_regeneration"),
                        builder -> jsonOperation(builder, "minimaxRegenerateVideo", "Regenerate video."))
                .POST("/aiproviders/{name}/minimax/video/v1",
                        request -> postJson(request, "/v1/video_generation"),
                        builder -> jsonOperation(builder, "minimaxCreateVideoV1", "Create legacy video task."))
                .GET("/aiproviders/{name}/minimax/video/v1/{taskId}",
                        request -> getJson(request, "/v1/query/video_generation",
                                Map.of("task_id", request.pathVariable("taskId"))),
                        builder -> jsonOperation(builder, "minimaxQueryVideoV1", "Query legacy video task."))
                .POST("/aiproviders/{name}/minimax/video/agent/templates",
                        request -> postJson(request, "/v1/video_template_generation"),
                        builder -> jsonOperation(builder, "minimaxCreateVideoTemplate", "Create template video."))
                .GET("/aiproviders/{name}/minimax/video/agent/templates/{taskId}",
                        request -> getJson(request, "/v1/query/video_template_generation",
                                Map.of("task_id", request.pathVariable("taskId"))),
                        builder -> jsonOperation(builder, "minimaxQueryVideoTemplate", "Query template video."))
                .POST("/aiproviders/{name}/minimax/music/generations",
                        request -> postJson(request, "/v1/music_generation"),
                        builder -> jsonOperation(builder, "minimaxGenerateMusic", "Generate music."))
                .POST("/aiproviders/{name}/minimax/music/cover-preprocess",
                        request -> postJson(request, "/v1/music_cover_preprocess"),
                        builder -> jsonOperation(builder, "minimaxPreprocessMusicCover", "Preprocess music cover."))
                .POST("/aiproviders/{name}/minimax/music/lyrics",
                        request -> postJson(request, "/v1/lyrics_generation"),
                        builder -> jsonOperation(builder, "minimaxGenerateLyrics", "Generate lyrics."))
                .build();
    }

    private Mono<ServerResponse> postJson(ServerRequest request, String path) {
        return prepare(request)
                .flatMap(prepared -> body(request)
                        .flatMap(body -> client.post(prepared.provider(), prepared.account(), path, body)))
                .flatMap(this::jsonResponse);
    }

    private Mono<ServerResponse> postOrStream(ServerRequest request, String path) {
        return prepare(request).flatMap(prepared -> body(request).flatMap(body -> {
            if (body.path("stream").asBoolean(false)) {
                var stream = client.postStream(prepared.provider(), prepared.account(), path, body)
                        .map(node -> "data: " + AiProviderJsonSupport.write(
                                mapper, node, MinimaxProtocol.NAME) + "\n\n");
                return ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(stream, String.class);
            }
            return client.post(prepared.provider(), prepared.account(), path, body)
                    .flatMap(this::jsonResponse);
        }));
    }

    private Mono<ServerResponse> getJson(ServerRequest request, String path) {
        return getJson(request, path, Map.of());
    }

    private Mono<ServerResponse> getJson(
            ServerRequest request, String path, Map<String, String> overrides) {
        return prepare(request)
                .flatMap(prepared -> client.get(
                        prepared.provider(), prepared.account(), path, query(request, overrides)))
                .flatMap(this::jsonResponse);
    }

    private Mono<ServerResponse> deleteJson(ServerRequest request, String path) {
        return prepare(request)
                .flatMap(prepared -> client.delete(prepared.provider(), prepared.account(), path))
                .flatMap(this::jsonResponse);
    }

    private Mono<ServerResponse> uploadFile(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> body(request).flatMap(body -> {
                    var storageFileId = text(body, "storageFileId");
                    var purpose = text(body, "purpose");
                    if (!StringUtils.hasText(storageFileId)) {
                        return Mono.error(invalid("storageFileId 不能为空。"));
                    }
                    if (!StringUtils.hasText(purpose)) {
                        return Mono.error(invalid("purpose 不能为空。"));
                    }
                    return storage(storageFileId, MAX_FILE_BYTES)
                            .flatMap(stored -> client.upload(
                                    prepared.provider(),
                                    prepared.account(),
                                    MinimaxProtocol.FILES_UPLOAD_PATH,
                                    Map.of("purpose", purpose),
                                    Map.of(),
                                    filename(stored.file()),
                                    stored.file().getSpec().getContentType(),
                                    stored.content()));
                }))
                .flatMap(this::jsonResponse);
    }

    private Mono<ServerResponse> asr(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> body(request).flatMap(body -> {
                    var storageFileId = text(body, "storageFileId");
                    var model = text(body, "model");
                    if (!StringUtils.hasText(storageFileId)) {
                        return Mono.error(invalid("storageFileId 不能为空。"));
                    }
                    if (!StringUtils.hasText(model)) {
                        return Mono.error(invalid("model 不能为空。"));
                    }
                    var language = text(body, "language");
                    var headers = StringUtils.hasText(language)
                            ? Map.of("language", language)
                            : Map.<String, String>of();
                    return storage(storageFileId, MAX_ASR_BYTES)
                            .flatMap(stored -> client.upload(
                                    prepared.provider(),
                                    prepared.account(),
                                    "/v1/speech_to_text",
                                    Map.of("model", model),
                                    headers,
                                    filename(stored.file()),
                                    stored.file().getSpec().getContentType(),
                                    stored.content()));
                }))
                .flatMap(this::jsonResponse);
    }

    private Mono<ServerResponse> deleteFile(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> body(request).flatMap(body -> {
                    var fileId = text(body, "file_id");
                    if (!StringUtils.hasText(fileId)) {
                        fileId = text(body, "fileId");
                    }
                    if (!StringUtils.hasText(fileId)) {
                        return Mono.error(invalid("file_id 不能为空。"));
                    }
                    var requestBody = mapper.createObjectNode().put("file_id", fileId);
                    return client.post(
                            prepared.provider(), prepared.account(), MinimaxProtocol.FILES_DELETE_PATH, requestBody);
                }))
                .flatMap(this::jsonResponse);
    }

    private Mono<ServerResponse> fileContent(ServerRequest request) {
        var fileId = request.queryParam("fileId").filter(StringUtils::hasText).orElse(null);
        if (!StringUtils.hasText(fileId)) {
            return Mono.error(invalid("fileId 不能为空。"));
        }
        return prepare(request)
                .flatMap(prepared -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(client.content(
                                prepared.provider(),
                                prepared.account(),
                                MinimaxProtocol.FILES_CONTENT_PATH,
                                Map.of("file_id", fileId)), DataBuffer.class));
    }

    private Mono<Prepared> prepare(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> access.requireAccount(provider)
                        .map(account -> new Prepared(provider, account)));
    }

    private Mono<JsonNode> body(ServerRequest request) {
        return request.bodyToMono(String.class)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .map(this::readBody);
    }

    private JsonNode readBody(String body) {
        try {
            var node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw invalid("请求体必须是 JSON 对象。");
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw invalid("请求体不是合法的 JSON。");
        }
    }

    private static Map<String, String> fileId(ServerRequest request) {
        var fileId = request.queryParam("fileId").filter(StringUtils::hasText).orElse(null);
        return StringUtils.hasText(fileId) ? Map.of("file_id", fileId) : Map.of();
    }

    private static Map<String, String> query(
            ServerRequest request, Map<String, String> overrides) {
        var values = new LinkedHashMap<String, String>();
        request.queryParams().toSingleValueMap().forEach(values::put);
        values.putAll(overrides);
        return values;
    }

    private Mono<StoredFile> storage(String storageFileId, int maxBytes) {
        return storageTransfer
                .getFile(storageFileId)
                .onErrorMap(ExtensionNotFoundException.class, error -> fileNotFound())
                .switchIfEmpty(Mono.error(fileNotFound()))
                .flatMap(file -> {
                    if (file.getSpec() == null || file.getSpec().getState() != StorageFileState.ACTIVE) {
                        return Mono.error(fileNotFound());
                    }
                    return storageTransfer
                            .readBytes(file.getMetadata().getName(), maxBytes)
                            .onErrorMap(DataBufferLimitException.class, error -> tooLarge())
                            .filter(content -> content.length > 0)
                            .switchIfEmpty(Mono.error(invalid("存储文件内容为空。")))
                            .map(content -> new StoredFile(file, content));
                });
    }

    private static String filename(StorageFile file) {
        var originalName = file.getSpec().getOriginalName();
        return StringUtils.hasText(originalName) ? originalName : file.getMetadata().getName();
    }

    private Mono<ServerResponse> jsonResponse(JsonNode node) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AiProviderJsonSupport.write(mapper, node, MinimaxProtocol.NAME));
    }

    private static String text(JsonNode body, String field) {
        var value = body == null ? null : body.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static ApiException invalid(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, reason);
    }

    private static ApiException fileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_FILE_NOT_FOUND, "存储文件不存在或不可用。");
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, CODE_TOO_LARGE, "存储文件超过允许的大小。");
    }

    private static org.springdoc.core.fn.builders.operation.Builder jsonOperation(
            org.springdoc.core.fn.builders.operation.Builder builder,
            String operationId,
            String description) {
        return builder.operationId(operationId)
                .description(description)
                .tag(TAG)
                .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                .response(responseBuilder().implementation(JsonNode.class));
    }

    private record Prepared(AiProvider provider, AiProviderAccount account) {}

    private record StoredFile(StorageFile file, byte[] content) {}
}
