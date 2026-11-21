package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
class L2PipelineTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    AiMemoryL1Repository l1Repository;

    @Mock
    AiMemoryL2Repository l2Repository;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiMemoryGenerationLogRepository generationLogRepository;

    private L2Pipeline service;

    @BeforeEach
    void setUp() {
        service = new L2Pipeline(
                new MemoryPromptResolver(),
                l1Repository,
                l2Repository,
                conversationRepository,
                generationLogRepository);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void upsertsNewSceneAdvancesCursorAndWritesGenerationLog() {
        var promptReference = new AtomicReference<String>();
        var resolved = new ResolvedMemoryPrompt(
                AiMemoryPrompt.Layer.L2, MemoryPromptSource.INSTANCE, "instance-l2", 1, "关注场景分类");
        when(l1Repository.findActiveByScope("team-1", "agent-1", 0L, 50))
                .thenReturn(Flux.just(l1Record(11L, "m1", "content")));
        when(l2Repository.findByScope("team-1", "agent-1")).thenReturn(Flux.empty());
        when(l2Repository.upsert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L2SceneBlock>getArgument(0).withId(5L)));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());
        when(conversationRepository.advanceL2Cursor(9L, 11L)).thenReturn(Mono.just(true));

        var result = service.process(
                        request(0L, new MemoryPromptInput("BASE_L2", resolved)),
                        (prompt, newL1, existing) -> {
                            promptReference.set(prompt);
                            return List.of(new L2Pipeline.ExtractedScene(
                                    "release", "Release", "Summary", List.of(11L), BigDecimal.ONE));
                        })
                .block();

        assertThat(result).isNotNull();
        assertThat(result.blocks()).hasSize(1);
        assertThat(result.blocks().getFirst().id()).isEqualTo(5L);
        assertThat(result.l2Cursor()).isEqualTo(11L);
        assertThat(result.l1Processed()).isEqualTo(1);
        assertThat(result.generationLogs()).isEqualTo(1);
        assertThat(promptReference.get()).startsWith("BASE_L2").contains("关注场景分类");
        verify(generationLogRepository).insert(any(MemoryGenerationLog.class));
    }

    @Test
    void mergesExistingSceneL1IdsAndIncrementsHeat() {
        when(l1Repository.findActiveByScope("team-1", "agent-1", 11L, 50))
                .thenReturn(Flux.just(l1Record(12L, "m2", "new content")));
        when(l2Repository.findByScope("team-1", "agent-1")).thenReturn(Flux.just(existingScene()));
        when(l2Repository.upsert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L2SceneBlock>getArgument(0).withId(5L)));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());
        when(conversationRepository.advanceL2Cursor(9L, 12L)).thenReturn(Mono.just(true));
        var captor = ArgumentCaptor.forClass(L2SceneBlock.class);

        var result = service.process(
                        request(11L, new MemoryPromptInput("BASE_L2", null)),
                        (prompt, newL1, existing) -> List.of(new L2Pipeline.ExtractedScene(
                                "release",
                                "New title",
                                "New summary",
                                List.of(12L),
                                new BigDecimal("0.90"))))
                .block();

        assertThat(result).isNotNull();
        verify(l2Repository).upsert(captor.capture());
        var merged = captor.getValue();
        assertThat(merged.id()).isEqualTo(5L);
        assertThat(merged.title()).isEqualTo("New title");
        assertThat(merged.summary()).isEqualTo("New summary");
        assertThat(merged.l1Ids()).containsExactly(11L, 12L);
        assertThat(merged.heat()).isEqualTo(3);
        assertThat(merged.lastActiveAt()).isEqualTo(NOW);
        assertThat(result.blocks().getFirst().l1Ids()).containsExactly(11L, 12L);
    }

    @Test
    void rejectsOverlappingL1IdsAcrossScenes() {
        when(l1Repository.findActiveByScope("team-1", "agent-1", 0L, 50))
                .thenReturn(Flux.just(l1Record(11L, "m1", "one"), l1Record(12L, "m2", "two")));
        when(l2Repository.findByScope("team-1", "agent-1")).thenReturn(Flux.empty());

        StepVerifier.create(service.process(
                        request(0L, new MemoryPromptInput("BASE_L2", null)),
                        (prompt, newL1, existing) -> List.of(
                                new L2Pipeline.ExtractedScene(
                                        "scene-a", "A", "A summary", List.of(11L), BigDecimal.ONE),
                                new L2Pipeline.ExtractedScene(
                                        "scene-b", "B", "B summary", List.of(11L), BigDecimal.ONE))))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(generationLogRepository, conversationRepository);
        verify(l2Repository, never()).upsert(any());
    }

    @Test
    void rejectsSceneReferencesToUnknownL1Memories() {
        when(l1Repository.findActiveByScope("team-1", "agent-1", 0L, 50))
                .thenReturn(Flux.just(l1Record(11L, "m1", "one")));
        when(l2Repository.findByScope("team-1", "agent-1")).thenReturn(Flux.empty());

        StepVerifier.create(service.process(
                        request(0L, new MemoryPromptInput("BASE_L2", null)),
                        (prompt, newL1, existing) -> List.of(new L2Pipeline.ExtractedScene(
                                "scene-a", "A", "A summary", List.of(99L), BigDecimal.ONE))))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(generationLogRepository, conversationRepository);
        verify(l2Repository, never()).upsert(any());
    }

    @Test
    void skipsEverythingWhenNoNewL1MemoriesExist() {
        when(l1Repository.findActiveByScope("team-1", "agent-1", 0L, 50)).thenReturn(Flux.empty());

        var result = service.process(
                        request(0L, new MemoryPromptInput("BASE_L2", null)),
                        (prompt, newL1, existing) -> {
                            throw new AssertionError("不应调用场景提取器");
                        })
                .block();

        assertThat(result).isNotNull();
        assertThat(result.blocks()).isEmpty();
        assertThat(result.l2Cursor()).isZero();
        assertThat(result.l1Processed()).isZero();
        verifyNoInteractions(l2Repository, conversationRepository, generationLogRepository);
    }

    private static L2Pipeline.Request request(long afterL1Id, MemoryPromptInput prompt) {
        return new L2Pipeline.Request(
                "team-1", "agent-1", 9L, afterL1Id, 50, prompt, MemoryGenerationInput.empty());
    }

    private static L1MemoryRecord l1Record(long id, String recordId, String content) {
        return L1MemoryRecord.newRecord(
                        recordId,
                        new MemoryContext("team-1", "user-1", "agent-1", "session-1", null),
                        9L,
                        content,
                        MemoryType.EPISODIC,
                        50,
                        "release",
                        List.of(101L),
                        Map.of(),
                        "hash-" + recordId,
                        MemoryScope.PRIVATE,
                        false,
                        1,
                        List.of(NOW))
                .withId(id);
    }

    private static L2SceneBlock existingScene() {
        return new L2SceneBlock(
                5L,
                "team-1",
                "agent-1",
                "release",
                "Old title",
                "Old summary",
                List.of(11L),
                2,
                new BigDecimal("0.80"),
                NOW.minusSeconds(60));
    }
}
