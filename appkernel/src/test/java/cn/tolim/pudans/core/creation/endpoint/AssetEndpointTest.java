package cn.tolim.pudans.core.creation.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiCatalogItem;
import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.dto.AssetCatalogItem;
import cn.tolim.pudans.contract.creation.dto.AssetCatalogView;
import cn.tolim.pudans.contract.creation.dto.AssetFacets;
import cn.tolim.pudans.contract.creation.dto.AssetPage;
import cn.tolim.pudans.contract.creation.dto.AssetRegisterRequest;
import cn.tolim.pudans.contract.creation.dto.AssetReuseDraft;
import cn.tolim.pudans.core.creation.archive.AssetCatalog;
import cn.tolim.pudans.core.creation.archive.Assets;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AssetEndpointTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    Assets assets;

    @Mock
    AssetCatalog catalog;

    @Mock
    ReactiveRequestIdentity identity;

    @Mock
    CreationAudit audits;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AssetEndpoint(assets, catalog, identity, audits);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsEveryAssetForAdministrativeInspection() {
        var asset = asset(UUID.randomUUID());
        when(assets.allItems(isNull(), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(Mono.just(new AssetPage(List.of(asset), null, 20, null, NOW)));

        client.get()
                .uri("/assets/-/all?limit=20")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].id")
                .isEqualTo(asset.id().toString());

        verify(assets).allItems(isNull(), isNull(), isNull(), isNull(), eq(20));
    }

    @Test
    void returnsEveryAssetFacetForAdministrativeInspection() {
        when(assets.allFacets())
                .thenReturn(Mono.just(new AssetFacets(2, List.of(), List.of(), null, NOW)));

        client.get()
                .uri("/assets/-/all-facets")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.total")
                .isEqualTo(2);

        verify(assets).allFacets();
    }

    @Test
    void listsAssetsForTheResolvedOwnerAndWorkspace() {
        var asset = asset(UUID.randomUUID());
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(assets.items(ALICE, "image", null, "cat", null, 5))
                .thenReturn(Mono.just(new AssetPage(List.of(asset), null, 5, null, NOW)));

        client.get()
                .uri("/assets/-/items?kind=image&q=cat&limit=5")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].storageFileId")
                .isEqualTo("file-1")
                .jsonPath("$.limit")
                .isEqualTo(5);

        verify(assets).items(ALICE, "image", null, "cat", null, 5);
    }

    @Test
    void returnsRealFacetBuckets() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(assets.facets(ALICE)).thenReturn(Mono.just(
                new AssetFacets(1, List.of(), List.of(), "暂无资产。", NOW)));

        client.get()
                .uri("/assets/-/facets")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.total")
                .isEqualTo(1)
                .jsonPath("$.emptyState")
                .isEqualTo("暂无资产。");
    }

    @Test
    void registersAnExistingStorageFileAndRecordsAnAudit() {
        var asset = asset(UUID.randomUUID());
        var request = new AssetRegisterRequest(
                "file-1", "image", "creation", null, null, "sample", null, "prompt",
                Map.of(), null, null, null, null, null, List.of(), Map.of());
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(assets.register(any(AssetRegisterRequest.class), eq(ALICE))).thenReturn(Mono.just(asset));
        when(audits.record(any(), eq("ARCHIVE_ASSET"), eq("Asset"), eq(asset.id().toString()), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/assets/-/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(asset.id().toString())
                .jsonPath("$.storageFileId")
                .isEqualTo("file-1");

        verify(audits).record(
                any(), eq("ARCHIVE_ASSET"), eq("Asset"), eq(asset.id().toString()), anyMap());
    }

    @Test
    void reusesAnAssetWithoutCreatingAStorageRecord() {
        var assetId = UUID.randomUUID();
        var draft = new AssetReuseDraft(
                assetId, assetId, "file-1", null, "image", "creation", "prompt",
                Map.of("ratio", "1:1"), List.of("cat"), Map.of(), NOW);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(assets.reuse(ALICE, assetId)).thenReturn(Mono.just(draft));
        when(audits.record(any(), eq("REUSE_ASSET"), eq("Asset"), eq(assetId.toString()), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/assets/{id}/-/reuse", assetId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.sourceAssetId")
                .isEqualTo(assetId.toString())
                .jsonPath("$.storageFileId")
                .isEqualTo("file-1");
    }

    @Test
    void returnsTheCatalogEntriesBackedAssetCatalog() {
        var option = new AiCatalogItem("image", "图片", null, null, 10, Map.of());
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(catalog.assets(ALICE)).thenReturn(Mono.just(new AssetCatalogView(
                List.of(new AssetCatalogItem(option, 2)), List.of(), 2, null, NOW)));

        client.get()
                .uri("/catalogs/-/assets")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.kinds[0].option.key")
                .isEqualTo("image")
                .jsonPath("$.kinds[0].count")
                .isEqualTo(2);
    }

    private static Asset asset(UUID id) {
        return new Asset(
                id,
                "alice",
                "workspace-a",
                "image",
                "creation",
                "file-1",
                null,
                null,
                "sample",
                null,
                "prompt",
                Map.of(),
                "image/png",
                42L,
                1024,
                768,
                null,
                List.of(),
                Map.of(),
                CreationValues.ASSET_STATUS_ACTIVE,
                NOW,
                NOW);
    }
}
