package cn.tolim.pudans.core.creation.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiAssetCatalog;
import cn.tolim.pudans.contract.ai.dto.AiCatalogItem;
import cn.tolim.pudans.contract.ai.dto.AiExploreCatalog;
import cn.tolim.pudans.contract.creation.dto.AssetFacetBucket;
import cn.tolim.pudans.core.ai.catalog.AiCatalogs;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AssetCatalogTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    AiCatalogs entries;

    @Mock
    AssetRepository assets;

    private AssetCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new AssetCatalog(entries, assets);
        catalog.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsEmptyCatalogAndEmptyStateWithoutRealEntries() {
        when(entries.assets()).thenReturn(Mono.just(new AiAssetCatalog(List.of(), List.of())));
        when(assets.kindFacets(ALICE)).thenReturn(Mono.just(List.of()));
        when(assets.sourceFacets(ALICE)).thenReturn(Mono.just(List.of()));
        when(assets.count(ALICE)).thenReturn(Mono.just(0L));

        StepVerifier.create(catalog.assets(ALICE))
                .assertNext(view -> {
                    assertThat(view.kinds()).isEmpty();
                    assertThat(view.sources()).isEmpty();
                    assertThat(view.total()).isZero();
                    assertThat(view.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void mergesRealCatalogEntriesWithRealFacetCounts() {
        when(entries.assets()).thenReturn(Mono.just(new AiAssetCatalog(
                List.of(item("image", "图片", 10)),
                List.of(item("creation", "生成", 20)))));
        when(assets.kindFacets(ALICE)).thenReturn(Mono.just(List.of(new AssetFacetBucket("image", 3))));
        when(assets.sourceFacets(ALICE))
                .thenReturn(Mono.just(List.of(new AssetFacetBucket("creation", 3))));
        when(assets.count(ALICE)).thenReturn(Mono.just(3L));

        StepVerifier.create(catalog.assets(ALICE))
                .assertNext(view -> {
                    assertThat(view.kinds()).hasSize(1);
                    assertThat(view.kinds().getFirst().option().key()).isEqualTo("image");
                    assertThat(view.kinds().getFirst().count()).isEqualTo(3);
                    assertThat(view.sources().getFirst().option().label()).isEqualTo("生成");
                    assertThat(view.sources().getFirst().count()).isEqualTo(3);
                    assertThat(view.emptyState()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void exploreCatalogIsEmptyWithoutCatalogEntries() {
        when(entries.explore()).thenReturn(Mono.just(new AiExploreCatalog(List.of(), List.of())));

        StepVerifier.create(catalog.explore())
                .assertNext(view -> {
                    assertThat(view.catalog().categories()).isEmpty();
                    assertThat(view.catalog().filters()).isEmpty();
                    assertThat(view.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    private static AiCatalogItem item(String key, String label, int order) {
        return new AiCatalogItem(key, label, null, null, order, Map.of());
    }
}
