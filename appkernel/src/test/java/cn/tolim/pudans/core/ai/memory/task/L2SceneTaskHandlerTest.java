package cn.tolim.pudans.core.ai.memory.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2Pipeline;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryPromptInput;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.ai.SceneSegmentation;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import java.math.BigDecimal;
import java.time.Instant;
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
class L2SceneTaskHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static final long CONVERSATION_ID = 9L;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiMemoryL1Repository memoryL1Repository;

    @Mock
    AiMemoryL2Repository memoryL2Repository;

    @Mock
    SceneSegmentation sceneAi;

    @Mock
    L2Pipeline l2Pipeline;

    private L2SceneTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new L2SceneTaskHandler(
                conversationRepository,
                memoryL1Repository,
                memoryL2Repository,
                new MemoryPromptResolver(),
                sceneAi,
                l2Pipeline);
    }

    @Test
    void extractsScenesFromNewL1WithTheConversationCursor() {
        var conversation = conversation(40L);
        var newL1 = List.of(record(55L, "内容 55"));
        var existing = List.of(block("scene-1", List.of(41L)));
        var extracted = List.of(new L2Pipeline.ExtractedScene(
                "scene-1", "场景一", "场景摘要", List.of(55L), BigDecimal.ONE));
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.just(conversation));
        when(memoryL1Repository.findActiveByScope("team-a", "agent-a", 40L, 100))
                .thenReturn(Flux.fromIterable(newL1));
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.fromIterable(existing));
        when(sceneAi.extract("", newL1, existing)).thenReturn(Mono.just(extracted));
        when(l2Pipeline.process(any(), any()))
                .thenReturn(Mono.just(new L2Pipeline.Result(List.of(), 55L, 1, 1)));

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        var requestCaptor = ArgumentCaptor.forClass(L2Pipeline.Request.class);
        var extractorCaptor = ArgumentCaptor.forClass(L2Pipeline.SceneExtractor.class);
        verify(l2Pipeline).process(requestCaptor.capture(), extractorCaptor.capture());
        var request = requestCaptor.getValue();
        assertThat(request.teamId()).isEqualTo("team-a");
        assertThat(request.agentId()).isEqualTo("agent-a");
        assertThat(request.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(request.afterL1Id()).isEqualTo(40L);
        assertThat(request.limit()).isEqualTo(100);
        assertThat(request.prompt()).isEqualTo(MemoryPromptInput.none());
        assertThat(extractorCaptor.getValue().extract("ignored", newL1, existing)).isEqualTo(extracted);
    }

    @Test
    void completesSilentlyWhenNoNewL1Exists() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.just(conversation(40L)));
        when(memoryL1Repository.findActiveByScope("team-a", "agent-a", 40L, 100)).thenReturn(Flux.empty());

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verifyNoInteractions(sceneAi, l2Pipeline);
        verifyNoInteractions(memoryL2Repository);
    }

    @Test
    void completesSilentlyWhenConversationIsMissing() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verifyNoInteractions(memoryL1Repository, sceneAi, l2Pipeline);
    }

    @Test
    void completesSilentlyWithoutAConversationId() {
        StepVerifier.create(handler.handle(task(Map.of("teamId", "team-a", "agentId", "agent-a"))))
                .verifyComplete();

        verifyNoInteractions(conversationRepository, memoryL1Repository, sceneAi, l2Pipeline);
    }

    private static AiTask task(Map<String, Object> payload) {
        return new AiTask(
                5L,
                L2SceneTaskHandler.TASK_TYPE,
                null,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                null,
                0,
                payload,
                0,
                3,
                NOW,
                NOW,
                NOW);
    }

    private static Conversation conversation(Long l2Cursor) {
        return new Conversation(
                CONVERSATION_ID,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                null,
                "fast",
                "fast-group",
                "ACTIVE",
                100L,
                l2Cursor,
                4,
                0L,
                NOW,
                NOW,
                NOW);
    }

    private static L1MemoryRecord record(long id, String content) {
        return L1MemoryRecord.newRecord(
                        "m-" + id,
                        new MemoryContext("team-a", "user-1", "agent-a", "session-1", null),
                        CONVERSATION_ID,
                        content,
                        MemoryType.EPISODIC,
                        50,
                        "场景一",
                        List.of(101L),
                        Map.of(),
                        "hash-" + id,
                        MemoryScope.PRIVATE,
                        false,
                        1,
                        List.of(NOW))
                .withId(id);
    }

    private static L2SceneBlock block(String sceneKey, List<Long> l1Ids) {
        return new L2SceneBlock(
                null, "team-a", "agent-a", sceneKey, "标题", "摘要", l1Ids, 1, BigDecimal.ONE, NOW);
    }
}
