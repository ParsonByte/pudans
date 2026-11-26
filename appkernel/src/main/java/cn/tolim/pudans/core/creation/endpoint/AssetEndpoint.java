package cn.tolim.pudans.core.creation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.dto.AssetRegisterRequest;
import cn.tolim.pudans.core.creation.archive.AssetCatalog;
import cn.tolim.pudans.core.creation.archive.Assets;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AssetEndpoint implements CustomEndpoint {

    private static final String TAG = "AssetV1alpha1";

    private final Assets assets;

    private final AssetCatalog catalog;

    private final ReactiveRequestIdentity identity;

    private final CreationAudit audits;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/assets/-/facets", this::facets, builder -> builder.operationId("getAssetFacets")
                        .description("Get real asset kind and source facets for the current owner and workspace.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Asset facets")))
                .GET("/assets/-/items", this::items, builder -> builder.operationId("listAssets")
                        .description("List assets for the current owner and workspace with cursor pagination.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Asset page")))
                .GET("/assets/-/all", this::allItems, builder -> builder.operationId("listAllAssets")
                        .description("List assets across the platform with cursor pagination for administration.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Asset page")))
                .GET("/assets/-/all-facets", this::allFacets, builder -> builder.operationId("getAllAssetFacets")
                        .description("Get real asset kind and source facets across the platform.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Asset facets")))
                .GET("/assets/{name}/view", this::view, builder -> builder.operationId("viewAsset")
                        .description("View an asset and its parent asset chain.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Asset.class)))
                .POST("/assets/{name}/-/reuse", this::reuse, builder -> builder.operationId("reuseAsset")
                        .description("Build an editable reuse draft without uploading or copying the file.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Asset reuse draft")))
                .POST("/assets/-/register", this::register, builder -> builder.operationId("registerAsset")
                        .description("Register an existing ready storage file as a logical asset.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Asset.class)))
                .GET("/catalogs/-/assets", this::catalog, builder -> builder.operationId("getAssetCatalog")
                        .description("Get the catalog_entries-backed asset kind and source catalog with real counts.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Asset catalog")))
                .build();
    }

    private Mono<ServerResponse> facets(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(assets::facets)
                .flatMap(facets -> ServerResponse.ok().bodyValue(facets));
    }

    private Mono<ServerResponse> items(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> assets.items(
                        owner,
                        query(request, "kind"),
                        query(request, "source"),
                        query(request, "q"),
                        query(request, "cursor"),
                        integerQuery(request, "limit")))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> allItems(ServerRequest request) {
        return assets
                .allItems(
                        query(request, "kind"),
                        query(request, "source"),
                        query(request, "q"),
                        query(request, "cursor"),
                        integerQuery(request, "limit"))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> allFacets(ServerRequest request) {
        return assets.allFacets().flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> view(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> assets.view(owner, uuidPath(request, "asset-not-found")))
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> reuse(ServerRequest request) {
        var assetId = uuidPath(request, "asset-not-found");
        return identity.resolve(request).flatMap(owner -> assets.reuse(owner, assetId)).flatMap(draft -> {
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("sourceAssetId", draft.sourceAssetId());
            snapshot.put("storageFileId", draft.storageFileId());
            return audits.record(request, "REUSE_ASSET", "Asset", draft.sourceAssetId().toString(), snapshot)
                    .thenReturn(draft);
        }).flatMap(draft -> ServerResponse.ok().bodyValue(draft));
    }

    private Mono<ServerResponse> register(ServerRequest request) {
        return identity.resolve(request).flatMap(owner -> request
                .bodyToMono(AssetRegisterRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> assets.register(body, owner)))
                .flatMap(asset -> audits
                        .record(request, "ARCHIVE_ASSET", "Asset", asset.id().toString(), snapshot(asset))
                        .thenReturn(asset))
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> catalog(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(catalog::assets)
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private static Map<String, Object> snapshot(Asset asset) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("kind", asset.kind());
        snapshot.put("source", asset.source());
        snapshot.put("storageFileId", asset.storageFileId());
        snapshot.put("generationTaskId", asset.generationTaskId());
        snapshot.put("parentAssetId", asset.parentAssetId());
        return snapshot;
    }

    private static UUID uuidPath(ServerRequest request, String errorCode) {
        try {
            return UUID.fromString(request.pathVariable("name"));
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.NOT_FOUND, errorCode, "资源不存在。");
        }
    }

    private static String query(ServerRequest request, String name) {
        var value = request.queryParam(name).orElse(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Integer integerQuery(ServerRequest request, String name) {
        var raw = request.queryParam(name).orElse(null);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "asset-limit-invalid", "分页数量不是有效整数。");
        }
    }
}
