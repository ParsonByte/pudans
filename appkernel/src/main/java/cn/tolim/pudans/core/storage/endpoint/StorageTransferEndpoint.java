package cn.tolim.pudans.core.storage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.core.storage.driver.ByteRange;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.upload.UploadStatus;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StorageTransferEndpoint implements CustomEndpoint {

    private static final Duration DOWNLOAD_SOURCE_TTL = Duration.ofMinutes(5);

    private final StorageService storageService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "StorageTransferV1alpha1";
        return SpringdocRouteBuilder.route()
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

    private Mono<ServerResponse> content(ServerRequest request) {
        var name = request.pathVariable("name");
        storageService.verifyDownload(requireToken(request), name);
        var rangeHeader = request.headers().firstHeader(HttpHeaders.RANGE);
        return storageService.getFile(name).flatMap(file -> {
            var sourceOptions = new StorageSourceOptions(
                    true, file.getSpec().getOriginalName(), DOWNLOAD_SOURCE_TTL);
            return storageService
                    .sourceUrl(name, sourceOptions)
                    .flatMap(source ->
                            ServerResponse.status(HttpStatus.FOUND).location(source.url()).build())
                    .switchIfEmpty(proxyDownload(file, rangeHeader));
        });
    }

    private Mono<ServerResponse> proxyDownload(StorageFile file, String rangeHeader) {
        var fileId = file.getMetadata().getName();
        var range = parseRange(rangeHeader, file.getSpec().getSize());
        var fileName = StringUtils.hasText(file.getSpec().getOriginalName())
                ? file.getSpec().getOriginalName()
                : file.getMetadata().getName();
        return storageService.open(fileId, new StorageReadOptions(range)).flatMap(result -> {
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

    private Mono<ServerResponse> uploadStatus(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return storageService.uploadStatus(name).flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    private Mono<ServerResponse> putPart(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        var part = partNumber(request);
        var contentLength = request.headers().asHttpHeaders().getContentLength();
        return storageService
                .putPart(name, part, contentLength, request.body(BodyExtractors.toDataBuffers()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> partUrl(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return storageService
                .uploadPartUrl(name, partNumber(request))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> complete(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return request.bodyToMono(new ParameterizedTypeReference<List<UploadedPart>>() {})
                .defaultIfEmpty(List.of())
                .flatMap(parts -> storageService.completeUpload(name, parts))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    private Mono<ServerResponse> abort(ServerRequest request) {
        var name = request.pathVariable("name");
        verifyUpload(request, name);
        return storageService.abortUpload(name).then(ServerResponse.status(HttpStatus.NO_CONTENT).build());
    }

    private void verifyUpload(ServerRequest request, String name) {
        storageService.verifyUpload(requireToken(request), name);
    }

    private static String requireToken(ServerRequest request) {
        return request.queryParam("token")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "storage-token-missing", "缺少存储令牌。"));
    }

    private static int partNumber(ServerRequest request) {
        try {
            return Integer.parseInt(request.pathVariable("part"));
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-part-invalid", "分片编号无效。");
        }
    }

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
            if (start < 0 || end < start) {
                return null;
            }
            return new ByteRange(start, Math.min(end, size - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
