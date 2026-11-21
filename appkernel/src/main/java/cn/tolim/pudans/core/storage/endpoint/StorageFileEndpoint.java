package cn.tolim.pudans.core.storage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.download.DownloadUrl;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StorageFileEndpoint implements CustomEndpoint {

    private final StorageTransfer storageTransfer;

    // 注册回收站、恢复、彻底删除与下载地址路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "StorageFileV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST("/storagefiles/{name}/trash", this::trash, builder -> builder.operationId("trashStorageFile")
                        .description("Move a storage file to the recycle bin.")
                        .tag(tag)
                        .response(responseBuilder().implementation(StorageFile.class)))
                .POST("/storagefiles/{name}/restore", this::restore, builder -> builder.operationId("restoreStorageFile")
                        .description("Restore a storage file from the recycle bin.")
                        .tag(tag)
                        .response(responseBuilder().implementation(StorageFile.class)))
                .DELETE("/storagefiles/{name}/purge", this::purge, builder -> builder.operationId("purgeStorageFile")
                        .description("Permanently delete a storage file and its object.")
                        .tag(tag)
                        .response(responseBuilder().responseCode("204").description("File purged")))
                .POST("/storagefiles/{name}/download-url", this::downloadUrl, builder -> builder.operationId("createStorageFileDownloadUrl")
                        .description("Create a short-lived download URL for a storage file.")
                        .tag(tag)
                        .response(responseBuilder().implementation(DownloadUrl.class)))
                .build();
    }

    // 将指定文件移入回收站并返回最新记录。
    private Mono<ServerResponse> trash(ServerRequest request) {
        return storageTransfer
                .trash(request.pathVariable("name"))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    // 将回收站文件恢复为可读并返回最新记录。
    private Mono<ServerResponse> restore(ServerRequest request) {
        return storageTransfer
                .restore(request.pathVariable("name"))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    // 彻底删除文件及其存储对象。
    private Mono<ServerResponse> purge(ServerRequest request) {
        return storageTransfer
                .purge(request.pathVariable("name"))
                .then(ServerResponse.status(HttpStatus.NO_CONTENT).build());
    }

    // 生成文件的短期下载地址。
    private Mono<ServerResponse> downloadUrl(ServerRequest request) {
        return storageTransfer
                .downloadUrl(request.pathVariable("name"), null)
                .flatMap(url -> ServerResponse.ok().bodyValue(url));
    }
}
