package cn.tolim.pudans.core.ai.memory.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.memory.JevChangeType;
import cn.tolim.pudans.core.ai.memory.JevDecision;
import cn.tolim.pudans.core.ai.memory.JevDecisionType;
import cn.tolim.pudans.core.ai.memory.JevGate;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.L3PipelineService;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryPromptInput;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.PersonaTrigger;
import cn.tolim.pudans.core.ai.memory.ai.MemoryJevAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemoryPersonaAiService;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
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
class L3PersonaTaskHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static final long CONVERSATION_ID = 9L;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiMemoryL1Repository memoryL1Repository;

    @Mock
    AiMemoryL2Repository memoryL2Repository;

    @Mock
    AiMemoryL3Repository memoryL3Repository;

    @Mock
    MemoryJevAiService jevAi;

    @Mock
    MemoryPersonaAiService personaAi;

    @Mock
    L3PipelineService pipelineService;

    private L3PersonaTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new L3PersonaTaskHandler(
                conversationRepository,
                memoryL1Repository,
                memoryL2Repository,
                memoryL3Repository,
                new MemoryPromptResolver(),
                jevAi,
                personaAi,
                pipelineService);
    }

    @Test
    void buildsChangedScenesAndPrecomputesJevAndPersona() {
        var persona = persona("现有画像");
        var first = block("scene-1", "场景一", List.of(55L, 56L));
        var second = block("scene-2", "场景二", List.of(57L));
        var newL1 = List.of(record(55L, "内容 55"), record(56L, "内容 56"));
        var generated = generated("新画像");
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.just(conversation(40L)));
        when(memoryL3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.just(persona));
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.just(first, second));
        when(memoryL1Repository.findActiveByScope("team-a", "agent-a", 0L, 100))
                .thenReturn(Flux.fromIterable(newL1));
        when(memoryL1Repository.findByIds(List.of(55L, 56L))).thenReturn(Flux.fromIterable(newL1));
        when(jevAi.classify(eq("现有画像"), any())).thenReturn(Mono.just(JevChangeType.NEW_RULE));
        when(personaAi.generate("", persona, List.of(new JevGate.ChangedScene(
                        "scene-1", "场景一摘要", "内容 55\n内容 56"))))
                .thenReturn(Mono.just(generated));
        when(pipelineService.process(any(), any(), any())).thenReturn(Mono.just(
                new L3PipelineService.Result(
                        PersonaTrigger.Reason.NONE,
                        new JevDecision(JevDecisionType.DEFERRED, JevChangeType.NONE, "none"),
                        "",
                        null,
                        false)));

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        var requestCaptor = ArgumentCaptor.forClass(L3PipelineService.Request.class);
        var classifierCaptor = ArgumentCaptor.forClass(JevGate.ChangeClassifier.class);
        var generatorCaptor = ArgumentCaptor.forClass(L3PipelineService.PersonaGenerator.class);
        verify(pipelineService).process(requestCaptor.capture(), classifierCaptor.capture(), generatorCaptor.capture());
        var request = requestCaptor.getValue();
        assertThat(request.teamId()).isEqualTo("team-a");
        assertThat(request.agentId()).isEqualTo("agent-a");
        assertThat(request.changedScenes()).containsExactly(
                new JevGate.ChangedScene("scene-1", "场景一摘要", "内容 55\n内容 56"));
        assertThat(request.prompt()).isEqualTo(MemoryPromptInput.none());
        var state = request.triggerState();
        assertThat(state.explicitRequested()).isFalse();
        assertThat(state.personaGenerated()).isTrue();
        assertThat(state.personaBodyPresent()).isTrue();
        assertThat(state.hasScenes()).isTrue();
        assertThat(state.scenesProcessed()).isEqualTo(2);
        assertThat(state.memoriesSinceLastPersona()).isEqualTo(2);
        assertThat(state.threshold()).isEqualTo(10);

        assertThat(classifierCaptor.getValue().classify("现有画像", request.changedScenes().getFirst()))
                .isEqualTo(JevChangeType.NEW_RULE);
        assertThat(classifierCaptor.getValue().classify(
                        "现有画像", new JevGate.ChangedScene("scene-2", "场景二摘要", "内容 57")))
                .isEqualTo(JevChangeType.NONE);
        assertThat(generatorCaptor.getValue().generate("ignored", persona, request.changedScenes()))
                .isEqualTo(generated);
        verify(jevAi).classify(eq("现有画像"), any());
        verify(personaAi).generate("", persona, request.changedScenes());
    }

    @Test
    void completesSilentlyWhenNoNewL1Exists() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.just(conversation(40L)));
        when(memoryL3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.empty());
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.empty());
        when(memoryL1Repository.findActiveByScope("team-a", "agent-a", 0L, 100)).thenReturn(Flux.empty());

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verifyNoInteractions(jevAi, personaAi, pipelineService);
        verify(memoryL1Repository, never()).findByIds(anyList());
    }

    @Test
    void skipsL1ThatPredatesTheCurrentPersona() {
        var persona = persona("现有画像", NOW.plusSeconds(60));
        var scene = block("scene-1", "场景一", List.of(55L));
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.just(conversation(40L)));
        when(memoryL3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.just(persona));
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.just(scene));
        when(memoryL1Repository.findActiveByScope("team-a", "agent-a", 0L, 100))
                .thenReturn(Flux.just(record(55L, "内容 55")));

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verifyNoInteractions(jevAi, personaAi, pipelineService);
        verify(memoryL1Repository, never()).findByIds(anyList());
    }

    @Test
    void completesSilentlyWhenConversationIsMissing() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verifyNoInteractions(memoryL1Repository, memoryL2Repository, memoryL3Repository);
        verifyNoInteractions(jevAi, personaAi, pipelineService);
    }

    private static AiTask task(Map<String, Object> payload) {
        return new AiTask(
                5L,
                L3PersonaTaskHandler.TASK_TYPE,
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

    private static L2SceneBlock block(String sceneKey, String title, List<Long> l1Ids) {
        return new L2SceneBlock(
                null, "team-a", "agent-a", sceneKey, title, title + "摘要", l1Ids, 1, BigDecimal.ONE, NOW);
    }

    private static L3Persona persona(String text) {
        return persona(text, NOW.minusSeconds(60));
    }

    private static L3Persona persona(String text, Instant refreshedAt) {
        return new L3Persona(null, "team-a", "agent-a", Map.of(), text, 3, 2, BigDecimal.ONE, refreshedAt);
    }

    private static L3PipelineService.GeneratedPersona generated(String text) {
        return new L3PipelineService.GeneratedPersona(Map.of(), text, 2, 2, BigDecimal.ONE);
    }
}
