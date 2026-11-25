package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.UnderstandingMap;
import cn.tolim.pudans.contract.ai.CatalogEntry;
import cn.tolim.pudans.core.agent.runtime.AgentModelRouteResolver;
import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddingModels;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.r2dbc.ai.catalog.CatalogEntryRepository;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapability;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapabilityProbe;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UnderstandingMapsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    CatalogEntryRepository entries;

    @Mock
    MemoryPersonalRepository memories;

    @Mock
    AgentModelRouteResolver routes;

    @Mock
    PgVectorCapabilityProbe capabilityProbe;

    @Mock
    MemoryEmbeddingModels embeddingModels;

    @Mock
    cn.tolim.pudans.core.agent.runtime.AgentModelRoute route;

    private UnderstandingMaps maps;

    @BeforeEach
    void setUp() {
        maps = new UnderstandingMaps(entries, memories, routes, capabilityProbe, embeddingModels);
        maps.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void combinesActiveCatalogDimensionsWithRealMemoryAggregates() {
        var preference = entry("preference", "偏好", 20, Map.of("emptyState", "还没有偏好记忆。"));
        var fact = entry("fact", "事实", 30, Map.of());
        when(entries.findActiveByKind("understanding_dimension", NOW))
                .thenReturn(Flux.just(preference, fact));
        when(memories.statsOwned(ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Flux.just(new MemoryPersonalRepository.MemoryTypeStat(
                        "preference", 3, new BigDecimal("0.8500"), NOW.minusSeconds(60))));

        StepVerifier.create(maps.map(ALICE))
                .assertNext(result -> {
                    assertThat(result.totalMemories()).isEqualTo(3);
                    assertThat(result.generatedAt()).isEqualTo(NOW);
                    assertThat(result.dimensions()).hasSize(2);
                    assertThat(result.dimensions().getFirst())
                            .satisfies(item -> {
                                assertThat(item.dimension()).isEqualTo("preference");
                                assertThat(item.memoryCount()).isEqualTo(3);
                                assertThat(item.averageConfidence())
                                        .isEqualByComparingTo("0.8500");
                                assertThat(item.lastUpdatedAt()).isEqualTo(NOW.minusSeconds(60));
                                assertThat(item.emptyState()).isEqualTo("还没有偏好记忆。");
                            });
                    assertThat(result.dimensions().get(1))
                            .satisfies(item -> {
                                assertThat(item.dimension()).isEqualTo("fact");
                                assertThat(item.memoryCount()).isZero();
                                assertThat(item.averageConfidence()).isNull();
                                assertThat(item.lastUpdatedAt()).isNull();
                                assertThat(item.emptyState()).isEqualTo("暂无相关记忆。");
                            });
                })
                .verifyComplete();
    }

    @Test
    void returnsAnEmptyMapWhenNoDimensionIsConfigured() {
        when(entries.findActiveByKind("understanding_dimension", NOW))
                .thenReturn(Flux.empty());
        when(memories.statsOwned(ALICE.ownerId(), ALICE.workspaceId()))
                .thenReturn(Flux.empty());

        StepVerifier.create(maps.map(ALICE))
                .assertNext(result -> {
                    assertThat(result.dimensions()).isEmpty();
                    assertThat(result.totalMemories()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void reportsBothRuntimeCapabilitiesWhenRealModelsAndIndexesResolve() {
        when(routes.resolve(ALICE.workspaceId(), null)).thenReturn(Mono.just(route));
        when(capabilityProbe.probe()).thenReturn(Mono.just(
                new PgVectorCapability(true, true, true, "memory-vector-ready")));
        when(embeddingModels.defaultSpace(ALICE.workspaceId()))
                .thenReturn(Mono.just(new MemoryEmbeddingModels.Space(null, null, null)));

        StepVerifier.create(maps.status(ALICE))
                .assertNext(result -> {
                    assertThat(result.extractionConfigured()).isTrue();
                    assertThat(result.vectorConfigured()).isTrue();
                    assertThat(result.extractionReasonCode()).isEqualTo("memory-extraction-ready");
                    assertThat(result.vectorReasonCode()).isEqualTo("memory-vector-ready");
                    assertThat(result.checkedAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void reportsStableReasonCodesWithoutConfiguredModels() {
        when(routes.resolve(ALICE.workspaceId(), null)).thenReturn(Mono.error(new ApiException(
                HttpStatus.NOT_FOUND,
                "ai-model-default-missing",
                "当前工作区尚未配置该能力的默认模型。")));
        when(capabilityProbe.probe()).thenReturn(Mono.just(
                new PgVectorCapability(false, false, false, "memory-vector-extension-missing")));

        StepVerifier.create(maps.status(ALICE))
                .assertNext(result -> {
                    assertThat(result.extractionConfigured()).isFalse();
                    assertThat(result.vectorConfigured()).isFalse();
                    assertThat(result.extractionReasonCode()).isEqualTo("ai-model-default-missing");
                    assertThat(result.vectorReasonCode()).isEqualTo("memory-vector-extension-missing");
                })
                .verifyComplete();
    }

    private static CatalogEntry entry(
            String key, String label, int order, Map<String, Object> metadata) {
        return new CatalogEntry(
                UUID.randomUUID(),
                "understanding_dimension",
                key,
                label,
                label + "描述",
                null,
                order,
                true,
                metadata,
                NOW.minusSeconds(600),
                null,
                NOW.minusSeconds(600),
                NOW.minusSeconds(600));
    }
}
