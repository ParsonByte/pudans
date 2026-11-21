package cn.tolim.pudans.core.storage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.ByteRange;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.upload.StorageUploadRequest;
import cn.tolim.pudans.core.storage.upload.UploadStatus;
import cn.tolim.pudans.core.storage.upload.UploadTicket;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StorageTransferEndpoint implements CustomEndpoint {

    private static final Duration DOWNLOAD_SOURCE_TTL = Duration.ofMinutes(5);

    private final StorageTransfer storageTransfer;

    // 注册上传会话、分片、下载与状态查询路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "StorageTransferV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST("/storagefiles/-/upload", this::initiateUpload, builder -> builder.operationId("initiateStorageUpload")
                        .description("Create an upload session and return its upload ticket.")
                        .tag(tag)
                        .response(responseBuilder().implementation(UploadTicket.class)))
                .POST("/storagefiles/{name}/upload/token", this::uploadToken, builder -> builder.operationId("issueStorageUploadToken")
                        .description("Issue a new upload token for an existing upload session.")
                        .tag(tag)
                        .response(responseBuilder().responseCode("200").description("Upload token")))
                .GET("/storagefiles/{name}/content", this::content, builder -> builder.operationId("downloadStorageContent")
                        .description("Download a storage file through the backend or a presigned redirect.")
                        .tag(tag)
                        .response(responseBuilder().responseCode("200").description("File content")))
                .GET("/storagefiles/{name}/upload", this::uploadStatus, builder -> builder.operationId("getStorageUploadStatus")
                        .description("Get the status of an upload session.")
                        .tag(tag)
                        .response(responseBuilder().implementation(UploadStatus.class)))
                .PUT("/storagefiles/{name}/upload/parts/{part}", this::putPart, builder -> builder.operationId("uploadStoragePart")
                        .description("Upload one part of an upload session.")
                        .tag(tag)
                        .response(responseBuilder().implementation(UploadedPart.class)))
                .GET("/storagefiles/{name}/upload/parts/{part}/url", this::partUrl, builder -> builder.operationId("presignStoragePart")
                        .description("Get a presigned URL for one upload part.")
                        .tag(tag)
                        .response(responseBuilder().responseCode("200").description("Presigned part URL")))
                .POST("/storagefiles/{name}/upload/complete", this::complete, builder -> builder.operationId("completeStorageUpload")
                        .description("Complete an upload session.")
                        .tag(tag)
                        .response(responseBuilder().implementation(StorageFile.class)))
                .DELETE("/storagefiles/{name}/upload", this::abort, builder -> builder.operationId("abortStorageUpload")
                        .description("Abort an upload session.")
                        .tag(tag)
                        .response(responseBuilder().responseCode("204").description("Upload aborted")))
                .build();
    }

    // 校验下载令牌，优先重定向直链否则代理下载。
    private Mono<ServerResponse> content(ServerRequest request) {
        var name = request.pathVariable("name");
        storageTransfer.verifyDownload(requireToken(request), name);
        var rangeHeader = request.headers().firstHeader(HttpHeaders.RANGE);
        return storageTransfer.getFile(name).flatMap(file -> {
            var sourceOptions = new StorageSourceOptions(
                    true, file.getSpec().getOriginalName(), DOWNLOAD_SOURCE_TTL);
            return storageTransfer
                    .sourceUrl(name, sourceOptions)
                    .flatMap(source ->
                            ServerResponse.status(HttpStatus.FOUND).location(source.url()).build())
                    .switchIfEmpty(proxyDownload(file, rangeHeader));
        });
    }

    // 通过后端流式输出内容并支持范围请求。
    private Mono<ServerResponse> proxyDownload(StorageFile file, String rangeHeader) {
        var fileId = file.getMetadata().getName();
        var range = parseRange(rangeHeader, file.getSpec().getSize());
        var fileName = StringUtils.hasText(file.getSpec().getOriginalName())
                ? file.getSpec().getOriginalName()
                : file.getMetadata().getName();
        return storageTransfer.open(fileId, new StorageReadOptions(range)).flatMap(result -> {
            var status = range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
            return ServerResponse.status(status).headers(headers -> {
                headers.setContentType(contentType(file.getSpec().getContentType()));
                headers.setContentLength(result.contentLength());
                headers.setContentDisposition(ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build());
                headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
                headers.set(HttpHeaders.ETAG, "\"" + result.object().etag() + "\"");
                if (result.object().lastModified() != null) {
                    headers.setLastModified(result.object().lastModified());
                }
                if (range != null) {
                    headers.set(
                            HttpHeaders.CONTENT_RANGE,
                            "bytes " + result.rangeStart() + "-" + result.rangeEnd() + "/"
                                    + result.object().size());
                }
            }).body(BodyInserters.fromDataBuffers(result.body()));
        });
    }

    // 上传归属必须来自当前登录用户，不能由请求体指定
    private Mono<ServerResponse> initiateUpload(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.UNAUTHORIZED, "storage-owner-required", "需要身份认证。")))
                .flatMap(owner -> request.bodyToMono(StorageUploadRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                        .map(body -> withOwner(body, owner)))
                .flatMap(storageTransfer::initiateUpload)
                .flatMap(ticket -> ServerResponse.ok().bodyValue(ticket));
    }

    // 复制请求并强制使用认证用户作为归属
    private static StorageUploadRequest withOwner(StorageUploadRequest body, String owner) {
        return new StorageUploadRequest(
                body.policy(),
                owner,
                body.module(),
                body.originPath(),
                body.originalName(),
                body.contentType(),
                body.size(),
                body.attributes(),
                body.sha256());
    }

    // 为上传会话续签上传令牌。
    private Mono<ServerResponse> uploadToken(ServerRequest request) {
        var name = request.pathVariable("name");
        return storageTransfer
                .issueUploadToken(name)
                .flatMap(token -> ServerResponse.ok().bodyValue(Map.of("token", token)));
    }

    // 校验上传令牌后返回会话进度。
    private Mono<ServerResponse> uploadStatus(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return storageTransfer.uploadStatus(name).flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    // 校验上传令牌后接收单个分片。
    private Mono<ServerResponse> putPart(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        var part = partNumber(request);
        var contentLength = request.headers().asHttpHeaders().getContentLength();
        return storageTransfer
                .putPart(name, part, contentLength, request.body(BodyExtractors.toDataBuffers()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 校验上传令牌后返回分片预签名地址。
    private Mono<ServerResponse> partUrl(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return storageTransfer
                .uploadPartUrl(name, partNumber(request))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 校验上传令牌后完成上传会话。
    private Mono<ServerResponse> complete(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return request.bodyToMono(new ParameterizedTypeReference<List<UploadedPart>>() {})
                .defaultIfEmpty(List.of())
                .flatMap(parts -> storageTransfer.completeUpload(name, parts))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    // 校验上传令牌后终止上传会话。
    private Mono<ServerResponse> abort(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return storageTransfer.abortUpload(name).then(ServerResponse.status(HttpStatus.NO_CONTENT).build());
    }

    // 校验请求携带的上传令牌。
    private void verifyUpload(ServerRequest request, String name) {
        storageTransfer.verifyUpload(requireToken(request), name);
    }

    // 读取存储令牌，缺失时拒绝请求。
    private static String requireToken(ServerRequest request) {
        return request.queryParam("token")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "storage-token-missing", "缺少存储令牌。"));
    }

    // 解析分片编号，非法值转为参数错误。
    private static int partNumber(ServerRequest request) {
        try {
            return Integer.parseInt(request.pathVariable("part"));
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-part-invalid", "分片编号无效。");
        }
    }

    // 解析内容类型，非法时回退为二进制流。
    private static MediaType contentType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // 解析单段 Range 头，无法解析时按完整响应处理。
    private static ByteRange parseRange(String header, long size) {
        if (!StringUtils.hasText(header) || size <= 0 || !header.startsWith("bytes=")) {
            return null;
        }
        var value = header.substring("bytes=".length()).trim();
        if (value.contains(",")) {
            return null;
        }
        var dash = value.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            var startText = value.substring(0, dash).trim();
            var endText = value.substring(dash + 1).trim();
            long start;
            long end;
            if (startText.isEmpty()) {
                var suffix = Long.parseLong(endText);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(startText);
                end = endText.isEmpty() ? size - 1 : Long.parseLong(endText);
            }
            if (start < 0) {
                return null;
            }
            if (start >= size || end < start) {
                throw rangeNotSatisfiable();
            }
            return new ByteRange(start, Math.min(end, size - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 构造字节范围不可满足错误。
    private static ApiException rangeNotSatisfiable() {
        return new ApiException(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "storage-range-not-satisfiable",
                "请求的字节范围无法满足。");
    }
}
