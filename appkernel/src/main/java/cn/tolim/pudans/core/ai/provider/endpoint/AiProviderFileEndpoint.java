package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.provider.AiProviderFile;
import cn.tolim.pudans.core.ai.provider.AiProviderFilePage;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekFilesClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProtocol;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.core.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiProviderFileEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private static final String CODE_INVALID = "ai-provider-files-invalid";

    private static final String CODE_FILE_NOT_FOUND = "ai-provider-file-not-found";

    private static final String CODE_TOO_LARGE = "ai-provider-file-too-large";

    private static final int DEFAULT_LIMIT = 1000;

    private static final int MAX_LIMIT = 1000;

    private static final int MIN_EXPIRY_SECONDS = 3600;

    private static final int MAX_EXPIRY_SECONDS = 2592000;

    private static final int MAX_FILE_BYTES = 100 * 1024 * 1024;

    private final DeepSeekProviderAccess access;

    private final DeepSeekFilesClient filesClient;

    private final StorageTransfer storageTransfer;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiproviders/{name}/files", this::upload, builder -> builder
                        .operationId("uploadAiProviderFile")
                        .description("Upload a stored file to the provider file store.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true).implementation(FileUploadRequest.class))
                        .response(responseBuilder().implementation(AiProviderFile.class)))
                .GET("/aiproviders/{name}/files", this::list, builder -> builder
                        .operationId("listAiProviderFiles")
                        .description("List files stored by the provider.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .parameter(queryParameter("after", "Return files after this file id."))
                        .parameter(queryParameter("limit", "Page size (1-1000, default 1000)."))
                        .parameter(queryParameter("order", "Creation time order: asc or desc."))
                        .parameter(queryParameter("purpose", "Filter by file purpose."))
                        .response(responseBuilder().implementation(AiProviderFilePage.class)))
                .GET("/aiproviders/{name}/files/{fileId}", this::get, builder -> builder
                        .operationId("getAiProviderFile")
                        .description("Get a file stored by the provider.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .parameter(pathParameter("fileId", "Provider file id."))
                        .response(responseBuilder().implementation(AiProviderFile.class)))
                .DELETE("/aiproviders/{name}/files/{fileId}", this::delete, builder -> builder
                        .operationId("deleteAiProviderFile")
                        .description("Delete a file stored by the provider.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .parameter(pathParameter("fileId", "Provider file id."))
                        .response(responseBuilder().responseCode("204").description("File deleted.")))
                .build();
    }

    private Mono<ServerResponse> upload(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> request.bodyToMono(FileUploadRequest.class)
                        .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                        .flatMap(body -> upload(provider, body)))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    private Mono<AiProviderFile> upload(AiProvider provider, FileUploadRequest body) {
        if (!StringUtils.hasText(body.storageFileId())) {
            return Mono.error(invalid("存储文件标识不能为空。"));
        }
        if (!DeepSeekProtocol.FILE_PURPOSES.contains(body.purpose())) {
            return Mono.error(invalid(
                    "文件用途必须是 user_data、text_recognition 或 rich_layout_recognition。"));
        }
        if (body.expiresAfterSeconds() != null
                && (body.expiresAfterSeconds() < MIN_EXPIRY_SECONDS
                        || body.expiresAfterSeconds() > MAX_EXPIRY_SECONDS)) {
            return Mono.error(invalid("文件有效期必须在 3600 到 2592000 秒之间。"));
        }
        return storageTransfer
                .getFile(body.storageFileId())
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
                            .flatMap(content -> access.requireAccount(provider)
                                    .flatMap(account -> filesClient.upload(
                                            provider,
                                            account,
                                            filename(file),
                                            file.getSpec().getContentType(),
                                            content,
                                            body.purpose(),
                                            body.expiresAfterSeconds())));
                });
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        var after = queryParam(request, "after");
        var order = queryParam(request, "order");
        var purpose = queryParam(request, "purpose");
        if (order != null && !"asc".equals(order) && !"desc".equals(order)) {
            return Mono.error(invalid("排序必须是 asc 或 desc。"));
        }
        if (purpose != null && !DeepSeekProtocol.FILE_PURPOSES.contains(purpose)) {
            return Mono.error(invalid(
                    "文件用途必须是 user_data、text_recognition 或 rich_layout_recognition。"));
        }
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> access.requireAccount(provider)
                        .flatMap(account -> filesClient.list(provider, account, limit, order, after, purpose)))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> access.requireAccount(provider)
                        .flatMap(account -> filesClient.get(
                                provider, account, request.pathVariable("fileId"))))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> access.requireAccount(provider)
                        .flatMap(account -> filesClient.delete(
                                provider, account, request.pathVariable("fileId"))))
                .then(ServerResponse.noContent().build());
    }

    private static String filename(StorageFile file) {
        var originalName = file.getSpec().getOriginalName();
        return StringUtils.hasText(originalName) ? originalName : file.getMetadata().getName();
    }

    private static String queryParam(ServerRequest request, String name) {
        return request.queryParam(name).filter(StringUtils::hasText).orElse(null);
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

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description);
    }

    private static Builder queryParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(false).description(description);
    }

    public record FileUploadRequest(
            String storageFileId, String purpose, Long expiresAfterSeconds) {}
}
