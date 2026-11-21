package cn.tolim.pudans.core.ai.memory.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiMemoryAsset;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryAssetRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryLayersTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    MemoryAssets memoryAssets;

    @Mock
    AiMemoryAssetRepository assetRepository;

    @Mock
    AiMemoryL1Repository l1Repository;

    @Mock
    AiMemoryL2Repository l2Repository;

    @Mock
    AiMemoryL3Repository l3Repository;

    private MemoryLayers service;

    @BeforeEach
    void setUp() {
        service = new MemoryLayers(
                memoryAssets, assetRepository, l1Repository, l2Repository, l3Repository);
    }

    @Test
    void rejectsInvalidPaginationBeforeTouchingRepositories() {
        assertThatThrownBy(() -> service.listL1("user-1", "asset-a", "team-a", 0L, null, null, null, 500))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(MemoryLayers.CODE_LAYER_INVALID);
    }

    @Test
    void updateL1RequiresOwnerAndWritesStrippedContent() {
        when(memoryAssets.requireOwner("user-1", "asset-a", "team-a")).thenReturn(Mono.just(assetRef()));
        var updated = new MemoryL1Item(
                4L,
                "record-4",
                "新内容",
                MemoryType.EPISODIC,
                MemoryScope.PRIVATE,
                50,
                "general",
                2,
                MemoryStatus.ACTIVE,
                NOW,
                Map.of());
        when(l1Repository.updateContent("team-a", "agent-a", "user-1", 4L, "新内容"))
                .thenReturn(Mono.just(updated));

        StepVerifier.create(service.updateL1("user-1", "asset-a", "team-a", 4L, "  新内容  "))
                .assertNext(item -> assertThat(item.content()).isEqualTo("新内容"))
                .verifyComplete();
    }

    @Test
    void updateL2ByPathPreservesMetadataBlock() {
        when(memoryAssets.requireOwner("user-1", "asset-a", "team-a")).thenReturn(Mono.just(assetRef()));
        var existing = scene("[META]{\"summary\":\"旧\"}[/META]旧正文");
        when(l2Repository.findByKey("team-a", "agent-a", "release")).thenReturn(Mono.just(existing));
        when(l2Repository.updateContent(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(service.updateL2(
                        "user-1", "asset-a", "team-a", "release", "release", "新正文", "新标题"))
                .assertNext(detail -> assertThat(detail.path()).isEqualTo("release"))
                .verifyComplete();

        var summary = ArgumentCaptor.forClass(String.class);
        verify(l2Repository)
                .updateContent(eq("team-a"), eq("agent-a"), eq(5L), eq("新标题"), summary.capture());
        assertThat(summary.getValue()).isEqualTo("[META]{\"summary\":\"旧\"}[/META]新正文");
    }

    @Test
    void updateL2WithoutPathFallsBackToNumericId() {
        when(memoryAssets.requireOwner("user-1", "asset-a", "team-a")).thenReturn(Mono.just(assetRef()));
        when(l2Repository.findById("team-a", "agent-a", 5L)).thenReturn(Mono.just(scene("正文")));
        when(l2Repository.updateContent(anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(Mono.just(scene("正文")));

        StepVerifier.create(service.updateL2("user-1", "asset-a", "team-a", "5", null, "正文", null))
                .expectNextCount(1)
                .verifyComplete();

        verify(l2Repository).findById("team-a", "agent-a", 5L);
    }

    @Test
    void listL0ReadsMessagesFromTheAssetScope() {
        when(memoryAssets.requireRead("user-1", "asset-a", "team-a")).thenReturn(Mono.just(assetRef()));
        when(assetRepository.listMessages("team-a", "user-1", "agent-a", 100L, null, null, "发布", 20))
                .thenReturn(Flux.just(message()));

        StepVerifier.create(service.listL0("user-1", "asset-a", "team-a", 100L, null, null, " 发布 ", 20))
                .assertNext(message -> {
                    assertThat(message.id()).isEqualTo(9L);
                    assertThat(message.content()).isEqualTo("发布流程");
                })
                .verifyComplete();
    }

    @Test
    void deleteL0DeletesSelectedMessagesWithinTheScope() {
        when(memoryAssets.requireOwner("user-1", "asset-a", "team-a")).thenReturn(Mono.just(assetRef()));
        when(assetRepository.deleteMessages(
                        "team-a", "user-1", "agent-a", List.of(9L), List.of("session-1")))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(service.deleteL0("user-1", "asset-a", "team-a", List.of(9L), List.of("session-1")))
                .expectNext(1L)
                .verifyComplete();

        verify(assetRepository)
                .deleteMessages("team-a", "user-1", "agent-a", List.of(9L), List.of("session-1"));
    }

    @Test
    void deleteL0RejectsTooManyMessageIds() {
        var ids = new ArrayList<Long>();
        for (long index = 0; index < 5001; index++) {
            ids.add(index);
        }

        StepVerifier.create(service.deleteL0("user-1", "asset-a", "team-a", ids, null))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(MemoryLayers.CODE_LAYER_INVALID))
                .verify();
    }

    @Test
    void detailL3ReturnsEmptyPersonaWhenNoProfileExists() {
        when(memoryAssets.requireRead("user-1", "asset-a", "team-a")).thenReturn(Mono.just(assetRef()));
        when(l3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.empty());

        StepVerifier.create(service.detailL3("user-1", "asset-a", "team-a"))
                .assertNext(persona -> {
                    assertThat(persona.id()).isNull();
                    assertThat(persona.content()).isEmpty();
                })
                .verifyComplete();
    }

    private static MemoryAssets.AssetRef assetRef() {
        var spec = new AiMemoryAsset.Spec();
        spec.setDisplayName("记忆块");
        spec.setTeam("team-a");
        spec.setOwnerUserId("user-1");
        spec.setSourceUserId("user-1");
        spec.setSourceAgentId("agent-a");
        var asset = new AiMemoryAsset();
        asset.setSpec(spec);
        var metadata = new Metadata();
        metadata.setName("asset-a");
        asset.setMetadata(metadata);
        return new MemoryAssets.AssetRef(asset, true, "asset-a");
    }

    private static Message message() {
        return new Message(
                9L,
                100L,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                null,
                null,
                1,
                0,
                "user",
                "发布流程",
                "TEXT",
                null,
                null,
                NOW);
    }

    private static L2SceneBlock scene(String summary) {
        return new L2SceneBlock(
                5L,
                "team-a",
                "agent-a",
                "release",
                "发布场景",
                summary,
                List.of(4L),
                3,
                BigDecimal.ONE,
                NOW);
    }
}