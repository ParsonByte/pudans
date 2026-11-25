package cn.tolim.pudans.core.creation.archive;

import cn.tolim.pudans.contract.ai.dto.AiCatalogItem;
import cn.tolim.pudans.contract.creation.dto.AssetCatalogItem;
import cn.tolim.pudans.contract.creation.dto.AssetCatalogView;
import cn.tolim.pudans.contract.creation.dto.AssetFacetBucket;
import cn.tolim.pudans.contract.creation.dto.ExploreCatalogView;
import cn.tolim.pudans.core.ai.catalog.AiCatalogs;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AssetCatalog {

    private static final String ASSET_EMPTY_STATE = "暂无资产。";

    private static final String CATALOG_EMPTY_STATE = "暂无资产目录项。";

    private static final String EXPLORE_EMPTY_STATE = "暂无探索目录项。";

    private final AiCatalogs catalogs;

    private final AssetRepository assets;

    private Clock clock = Clock.systemUTC();

    public Mono<AssetCatalogView> assets(RequestIdentity identity) {
        var now = now();
        return Mono.zip(
                        catalogs.assets(),
                        assets.kindFacets(identity),
                        assets.sourceFacets(identity),
                        assets.count(identity))
                .map(tuple -> {
                    var catalog = tuple.getT1();
                    var kindCounts = counts(tuple.getT2());
                    var sourceCounts = counts(tuple.getT3());
                    var total = tuple.getT4();
                    var kinds = items(catalog.kinds(), kindCounts);
                    var sources = items(catalog.sources(), sourceCounts);
                    var emptyState = total == 0
                            ? ASSET_EMPTY_STATE
                            : kinds.isEmpty() && sources.isEmpty() ? CATALOG_EMPTY_STATE : null;
                    return new AssetCatalogView(
                            kinds,
                            sources,
                            total,
                            emptyState,
                            now);
                });
    }

    public Mono<ExploreCatalogView> explore() {
        var now = now();
        return catalogs
                .explore()
                .map(catalog -> {
                    var empty = catalog.categories().isEmpty() && catalog.filters().isEmpty();
                    return new ExploreCatalogView(
                            catalog,
                            empty ? EXPLORE_EMPTY_STATE : null,
                            now);
                });
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static List<AssetCatalogItem> items(
            List<AiCatalogItem> catalog, Map<String, Long> counts) {
        return catalog.stream()
                .map(option -> new AssetCatalogItem(option, counts.getOrDefault(option.key(), 0L)))
                .toList();
    }

    private static Map<String, Long> counts(List<AssetFacetBucket> buckets) {
        return buckets.stream()
                .collect(Collectors.toMap(
                        AssetFacetBucket::key,
                        AssetFacetBucket::count,
                        Long::sum,
                        LinkedHashMap::new));
    }

    private Instant now() {
        return clock.instant();
    }
}
