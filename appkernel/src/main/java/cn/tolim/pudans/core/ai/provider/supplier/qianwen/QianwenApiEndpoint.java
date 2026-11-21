package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.core.ai.provider.supplier.qianwen.QianwenApiClient.ProxyResponse;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class QianwenApiEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private static final String PREFIX = "/aiproviders/{name}/qianwen";

    private static final String CODE_INVALID = "qianwen-request-invalid";

    private static final String CODE_FILE_NOT_FOUND = "qianwen-file-not-found";

    private static final String CODE_TOO_LARGE = "qianwen-file-too-large";

    private static final int MAX_FILE_BYTES = 100 * 1024 * 1024;

    private static final String FILES_PATH = "/compatible-mode/v1/files";

    private final QianwenProviderAccess access;

    private final QianwenApiClient client;

    private final StorageTransfer storageTransfer;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST(PREFIX + "/files", this::uploadFile, builder -> jsonOperation(
                        builder, "qianwenUploadFile", "Upload a stored file to the compatible files API."))
                .POST(PREFIX + "/files/upload", this::uploadFile, builder -> jsonOperation(
                        builder, "qianwenUploadFileAlias", "Upload a stored file to the compatible files API."))
                .GET(PREFIX + "/files/{fileId}/content", this::fileContent, builder -> jsonOperation(
                        builder, "qianwenFileContent", "Download a compatible file's content."))
                .POST(PREFIX + "/{*path}", request -> proxy(request, HttpMethod.POST), builder -> proxyOperation(
                        builder, "qianwenProxyPost", "Proxy a POST request to the Qianwen API."))
                .PUT(PREFIX + "/{*path}", request -> proxy(request, HttpMethod.PUT), builder -> proxyOperation(
                        builder, "qianwenProxyPut", "Proxy a PUT request to the Qianwen API."))
                .PATCH(PREFIX + "/{*path}", request -> proxy(request, HttpMethod.PATCH), builder -> proxyOperation(
                        builder, "qianwenProxyPatch", "Proxy a PATCH request to the Qianwen API."))
                .GET(PREFIX + "/{*path}", request -> proxy(request, HttpMethod.GET), builder -> proxyOperation(
                        builder, "qianwenProxyGet", "Proxy a GET request to the Qianwen API."))
                .DELETE(PREFIX + "/{*path}", request -> proxy(request, HttpMethod.DELETE), builder -> proxyOperation(
                        builder, "qianwenProxyDelete", "Proxy a DELETE request to the Qianwen API."))
                .build();
    }

    private Mono<ServerResponse> proxy(ServerRequest request, HttpMethod method) {
        var path = QianwenProtocol.normalizePath(request.pathVariable("path"));
        return prepare(request)
                .flatMap(prepared -> body(request, method)
                        .defaultIfEmpty("")
                        .flatMap(value -> client.exchange(
                                prepared.provider(),
                                prepared.account(),
                                method,
                                path,
                                request.queryParams(),
                                value.isEmpty() ? null : value)))
                .flatMap(this::response);
    }

    private Mono<ServerResponse> uploadFile(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> body(request, HttpMethod.POST).flatMap(rawBody -> {
                    var body = readBody(rawBody);
                    var storageFileId = text(body, "storageFileId");
                    var purpose = text(body, "purpose");
                    if (!StringUtils.hasText(storageFileId)) {
                        return Mono.error(invalid("storageFileId 不能为空。"));
                    }
                    if (!"file-extract".equals(purpose) && !"batch".equals(purpose)) {
                        return Mono.error(invalid("purpose 必须是 file-extract 或 batch。"));
                    }
                    return storage(storageFileId)
                            .flatMap(stored -> client.postMultipart(
                                    prepared.provider(),
                                    prepared.account(),
                                    FILES_PATH,
                                    Map.of("purpose", purpose),
                                    filename(stored.file()),
                                    stored.file().getSpec().getContentType(),
                                    stored.content()));
                }))
                .flatMap(this::response);
    }

    private Mono<ServerResponse> fileContent(ServerRequest request) {
        var fileId = request.pathVariable("fileId");
        if (!StringUtils.hasText(fileId)) {
            return Mono.error(invalid("fileId 不能为空。"));
        }
        return prepare(request)
                .flatMap(prepared -> client.exchange(
                        prepared.provider(),
                        prepared.account(),
                        HttpMethod.GET,
                        FILES_PATH + "/" + fileId + "/content",
                        request.queryParams(),
                        null))
                .flatMap(this::response);
    }

    private Mono<Prepared> prepare(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> access.requireAccount(provider)
                        .map(account -> new Prepared(provider, account)));
    }

    private Mono<String> body(ServerRequest request, HttpMethod method) {
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            return Mono.empty();
        }
        return request.bodyToMono(String.class).defaultIfEmpty("");
    }

    private Mono<ServerResponse> response(ProxyResponse proxy) {
        var builder = ServerResponse.status(proxy.status());
        if (proxy.contentType() != null) {
            builder.contentType(proxy.contentType());
        }
        return builder.bodyValue(proxy.body());
    }

    private Mono<StoredFile> storage(String storageFileId) {
        return storageTransfer
                .getFile(storageFileId)
                .onErrorMap(ExtensionNotFoundException.class, error -> fileNotFound())
                .switchIfEmpty(Mono.error(fileNotFound()))
                .flatMap(file -> {
                    if (file.getSpec() == null || file.getSpec().getState() != StorageFileState.ACTIVE) {
                        return Mono.error(fileNotFound());
                    }
                    return storageTransfer
                            .readBytes(file.getMetadata().getName(), MAX_FILE_BYTES)
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

    private static Builder jsonOperation(Builder builder, String operationId, String description) {
        return builder.operationId(operationId)
                .description(description)
                .tag(TAG)
                .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                .response(responseBuilder().implementation(JsonNode.class));
    }

    private static Builder proxyOperation(Builder builder, String operationId, String description) {
        return builder.operationId(operationId)
                .description(description)
                .tag(TAG)
                .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                .parameter(parameterBuilder().in(ParameterIn.PATH).name("path").required(true))
                .requestBody(requestBodyBuilder().required(false).implementation(JsonNode.class))
                .response(responseBuilder().implementation(JsonNode.class));
    }

    private record Prepared(AiProvider provider, AiProviderAccount account) {}

    private record StoredFile(StorageFile file, byte[] content) {}
}
