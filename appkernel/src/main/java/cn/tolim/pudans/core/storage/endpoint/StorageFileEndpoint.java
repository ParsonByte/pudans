package cn.tolim.pudans.core.storage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.download.DownloadUrl;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StorageFileEndpoint implements CustomEndpoint {

    private final StorageTransfer storageTransfer;

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

    private Mono<ServerResponse> trash(ServerRequest request) {
        return storageTransfer
                .trash(request.pathVariable("name"))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    private Mono<ServerResponse> restore(ServerRequest request) {
        return storageTransfer
                .restore(request.pathVariable("name"))
                .flatMap(file -> ServerResponse.ok().bodyValue(file));
    }

    private Mono<ServerResponse> purge(ServerRequest request) {
        return storageTransfer
                .purge(request.pathVariable("name"))
                .then(ServerResponse.status(HttpStatus.NO_CONTENT).build());
    }

    private Mono<ServerResponse> downloadUrl(ServerRequest request) {
        return storageTransfer
                .downloadUrl(request.pathVariable("name"), null)
                .flatMap(url -> ServerResponse.ok().bodyValue(url));
    }
}
