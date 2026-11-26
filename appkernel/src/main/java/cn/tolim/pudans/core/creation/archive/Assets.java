package cn.tolim.pudans.core.creation.archive;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.dto.AssetFacetBucket;
import cn.tolim.pudans.contract.creation.dto.AssetFacets;
import cn.tolim.pudans.contract.creation.dto.AssetPage;
import cn.tolim.pudans.contract.creation.dto.AssetRegisterRequest;
import cn.tolim.pudans.contract.creation.dto.AssetReuseDraft;
import cn.tolim.pudans.contract.creation.dto.AssetView;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.r2dbc.creation.GenerationTaskStore;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Assets {

    private static final int MAX_ANCESTORS = 32;

    private static final String EMPTY_STATE = "暂无资产。";

    private final AssetRepository assets;

    private final GenerationTaskStore generationTasks;

    private final StorageTransfer storageTransfer;

    private Clock clock = Clock.systemUTC();

    public Mono<AssetPage> items(
            RequestIdentity identity,
            String kind,
            String source,
            String q,
            String cursor,
            Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "asset-cursor-invalid");
        return assets
                .list(identity, kind, source, q, position, limit + 1)
                .map(fetched -> page(fetched, limit));
    }

    public Mono<AssetPage> allItems(
            String kind,
            String source,
            String q,
            String cursor,
            Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "asset-cursor-invalid");
        return assets
                .listAll(kind, source, q, position, limit + 1)
                .map(fetched -> page(fetched, limit));
    }

    public Mono<AssetFacets> facets(RequestIdentity identity) {
        return facets(assets.kindFacets(identity), assets.sourceFacets(identity), assets.count(identity));
    }

    public Mono<AssetFacets> allFacets() {
        return facets(assets.kindFacetsAll(), assets.sourceFacetsAll(), assets.countAll());
    }

    private Mono<AssetFacets> facets(
            Mono<List<AssetFacetBucket>> kinds,
            Mono<List<AssetFacetBucket>> sources,
            Mono<Long> count) {
        return Mono.zip(kinds, sources, count)
                .map(tuple -> {
                    var total = tuple.getT3();
                    var empty = total == 0;
                    return new AssetFacets(
                            total,
                            empty ? List.of() : tuple.getT1(),
                            empty ? List.of() : tuple.getT2(),
                            empty ? EMPTY_STATE : null,
                            now());
                });
    }

    public Mono<AssetView> view(RequestIdentity identity, UUID assetId) {
        return require(identity, assetId)
                .flatMap(asset -> ancestors(identity, asset).map(parents -> new AssetView(asset, parents)));
    }

    public Mono<AssetReuseDraft> reuse(RequestIdentity identity, UUID assetId) {
        return require(identity, assetId).map(asset -> new AssetReuseDraft(
                asset.id(),
                asset.id(),
                asset.storageFileId(),
                asset.generationTaskId(),
                asset.kind(),
                asset.source(),
                asset.prompt(),
                asset.settings(),
                asset.tags(),
                asset.metadata(),
                now()));
    }

    public Mono<Asset> register(AssetRegisterRequest request, RequestIdentity identity) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "asset-register-body-required", "资产登记内容不能为空。"));
        }
        if (!StringUtils.hasText(request.storageFileId())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "asset-storage-file-required", "资产必须关联已创建的文件。"));
        }
        if (!StringUtils.hasText(request.kind()) || !StringUtils.hasText(request.source())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "asset-type-required", "资产类型和来源不能为空。"));
        }
        if (!CreationValues.ASSET_KINDS.contains(request.kind().trim())
                || !CreationValues.ASSET_SOURCES.contains(request.source().trim())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "asset-type-invalid", "资产类型或来源无效。"));
        }
        var storageFileId = request.storageFileId().trim();
        return validateGenerationTask(request.generationTaskId(), identity)
                .then(Mono.defer(() -> storageTransfer
                        .getFile(storageFileId)
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.NOT_FOUND, "asset-storage-file-not-found", "关联文件不存在。")))
                        .flatMap(file -> validateFile(file, identity))
                        .flatMap(file -> validateParent(request.parentAssetId(), identity).thenReturn(file))
                        .flatMap(file -> assets
                                .findByStorageFile(storageFileId, request.generationTaskId(), identity)
                                .switchIfEmpty(Mono.defer(() -> insert(request, storageFileId, file, identity))))))
                .onErrorMap(
                        DataIntegrityViolationException.class,
                        error -> new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "asset-reference-invalid",
                                "资产引用的生成记录或父资产无效。",
                                error));
    }

    public Mono<Asset> require(RequestIdentity identity, UUID assetId) {
        return assets
                .findOwned(assetId, identity)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "asset-not-found", "资产不存在。")));
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Mono<StorageFile> validateFile(StorageFile file, RequestIdentity identity) {
        var spec = file.getSpec();
        if (spec == null
                || !StringUtils.hasText(spec.getOwner())
                || !spec.getOwner().equals(identity.ownerId())) {
            return Mono.error(new ApiException(
                    HttpStatus.NOT_FOUND, "asset-storage-file-not-found", "关联文件不存在。"));
        }
        if (spec.getState() != StorageFileState.ACTIVE) {
            return Mono.error(new ApiException(
                    HttpStatus.CONFLICT, "asset-storage-file-not-ready", "关联文件尚未就绪。"));
        }
        return Mono.just(file);
    }

    private Mono<Void> validateGenerationTask(UUID generationTaskId, RequestIdentity identity) {
        if (generationTaskId == null) {
            return Mono.empty();
        }
        return generationTasks
                .findById(generationTaskId)
                .filter(task -> Objects.equals(task.ownerId(), identity.ownerId())
                        && Objects.equals(task.workspaceId(), identity.workspaceId()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        "asset-generation-task-not-found",
                        "生成记录不存在。")))
                .then();
    }

    private Mono<Void> validateParent(UUID parentAssetId, RequestIdentity identity) {
        if (parentAssetId == null) {
            return Mono.empty();
        }
        return require(identity, parentAssetId)
                .onErrorMap(
                        ApiException.class,
                        error -> new ApiException(
                                HttpStatus.NOT_FOUND, "asset-parent-not-found", "父资产不存在。"))
                .then();
    }

    private Mono<Asset> insert(
            AssetRegisterRequest request, String storageFileId, StorageFile file, RequestIdentity identity) {
        var now = now();
        var spec = file.getSpec();
        var asset = new Asset(
                UUID.randomUUID(),
                identity.ownerId(),
                identity.workspaceId(),
                request.kind().trim(),
                request.source().trim(),
                storageFileId,
                request.generationTaskId(),
                request.parentAssetId(),
                StringUtils.hasText(request.name()) ? request.name().trim() : spec.getOriginalName(),
                request.description(),
                request.prompt(),
                request.settings() == null ? Map.of() : request.settings(),
                StringUtils.hasText(request.contentType()) ? request.contentType() : spec.getContentType(),
                request.size() == null ? spec.getSize() : request.size(),
                request.width(),
                request.height(),
                request.durationMs(),
                request.tags() == null ? List.of() : request.tags(),
                request.metadata() == null ? Map.of() : request.metadata(),
                CreationValues.ASSET_STATUS_ACTIVE,
                now,
                now);
        return assets.insert(asset);
    }

    private Mono<List<Asset>> ancestors(RequestIdentity identity, Asset asset) {
        var seen = new HashSet<UUID>();
        seen.add(asset.id());
        var result = new ArrayList<Asset>();
        return loadAncestor(identity, asset.parentAssetId(), seen, result, 0)
                .then(Mono.fromSupplier(() -> List.copyOf(result)));
    }

    private Mono<Void> loadAncestor(
            RequestIdentity identity,
            UUID parentId,
            Set<UUID> seen,
            List<Asset> result,
            int depth) {
        if (parentId == null || depth >= MAX_ANCESTORS || !seen.add(parentId)) {
            return Mono.empty();
        }
        return assets
                .findOwned(parentId, identity)
                .flatMap(parent -> {
                    result.add(parent);
                    return loadAncestor(identity, parent.parentAssetId(), seen, result, depth + 1);
                })
                .then();
    }

    private AssetPage page(List<Asset> fetched, int limit) {
        var hasMore = fetched.size() > limit;
        var items = hasMore ? fetched.subList(0, limit) : fetched;
        var nextCursor = hasMore && !items.isEmpty()
                ? CreationCursor.encode(items.getLast().updatedAt(), items.getLast().id())
                : null;
        return new AssetPage(
                List.copyOf(items),
                nextCursor,
                limit,
                items.isEmpty() ? EMPTY_STATE : null,
                now());
    }

    private Instant now() {
        return clock.instant();
    }
}
