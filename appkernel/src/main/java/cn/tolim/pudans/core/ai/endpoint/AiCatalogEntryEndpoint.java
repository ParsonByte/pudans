package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.CatalogEntry;
import cn.tolim.pudans.contract.ai.dto.CatalogEntryCreateRequest;
import cn.tolim.pudans.contract.ai.dto.CatalogEntryUpdateRequest;
import cn.tolim.pudans.core.ai.catalog.CatalogEntries;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiCatalogEntryEndpoint implements CustomEndpoint {

    private static final String TAG = "CatalogEntryV1alpha1";

    private final CatalogEntries entries;

    // 注册可运营目录项管理路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/catalogentries", this::list, builder -> builder.operationId("listCatalogEntries")
                        .description("List catalog entries, optionally filtered by catalog kind.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").implementationArray(CatalogEntry.class)))
                .POST("/catalogentries", this::create, builder -> builder.operationId("createCatalogEntry")
                        .description("Create a catalog entry.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(CatalogEntryCreateRequest.class))))
                        .response(responseBuilder().implementation(CatalogEntry.class)))
                .GET("/catalogentries/{name}", this::get, builder -> builder.operationId("getCatalogEntry")
                        .description("Get a catalog entry.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(CatalogEntry.class)))
                .PATCH("/catalogentries/{name}", this::update, builder -> builder.operationId("updateCatalogEntry")
                        .description("Update a catalog entry.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder()
                                                .implementation(CatalogEntryUpdateRequest.class))))
                        .response(responseBuilder().implementation(CatalogEntry.class)))
                .DELETE("/catalogentries/{name}", this::delete, builder -> builder.operationId("deleteCatalogEntry")
                        .description("Delete a catalog entry.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("Catalog entry deleted")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(
                entries.list(request.queryParam("kind").orElse(null)), CatalogEntry.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CatalogEntryCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(entries::create)
                .flatMap(entry -> ServerResponse.status(HttpStatus.CREATED).bodyValue(entry));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return entries.get(id(request)).flatMap(entry -> ServerResponse.ok().bodyValue(entry));
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var id = id(request);
        return request.bodyToMono(CatalogEntryUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> entries.update(id, body))
                .flatMap(entry -> ServerResponse.ok().bodyValue(entry));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return entries.delete(id(request)).then(ServerResponse.noContent().build());
    }

    private static java.util.UUID id(ServerRequest request) {
        return AiEndpointSupport.uuid(
                request.pathVariable("name"),
                "catalog-entry-id-invalid",
                "目录项标识不是有效 UUID。");
    }
}
