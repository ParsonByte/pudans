package cn.tolim.pudans.core.ai.memory.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.memory.DedupDecision;
import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.L1DedupService;
import cn.tolim.pudans.core.ai.memory.L1PipelineService;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.SceneSegment;
import cn.tolim.pudans.core.ai.memory.ai.ExtractionOutcome;
import cn.tolim.pudans.core.ai.memory.ai.MemoryConflictAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemoryExtractionAiService;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
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
class L1ExtractTaskHandlerOrgCandidateTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static final long CONVERSATION_ID = 9L;

    private static final long TASK_ID = 5L;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiConversationService conversationService;

    @Mock
    AiMessageRepository messageRepository;

    @Mock
    AiMemoryL1Repository memoryL1Repository;

    @Mock
    AiMemoryL2Repository memoryL2Repository;

    @Mock
    AiMemoryL3Repository memoryL3Repository;

    @Mock
    AiSettingService settingService;

    @Mock
    MemoryExtractionAiService extractionAi;

    @Mock
    MemoryConflictAiService conflictAi;

    @Mock
    L1PipelineService pipelineService;

    private L1ExtractTaskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new L1ExtractTaskHandler(
                conversationRepository,
                conversationService,
                messageRepository,
                memoryL1Repository,
                memoryL2Repository,
                memoryL3Repository,
                settingService,
                extractionAi,
                conflictAi,
                new L1DedupService(),
                pipelineService);
    }

    @Test
    void passesOrgCandidateWhenADurableInstructionIsExtracted() {
        var request = captureRequest(memory(MemoryType.INSTRUCTION, 80, Map.of()));

        assertThat(request.orgCandidate()).isTrue();
    }

    @Test
    void passesOrgCandidateWhenMetadataMarksAReusableFact() {
        var request = captureRequest(memory(MemoryType.WORK_FACT, 50, Map.of("orgCandidate", true)));

        assertThat(request.orgCandidate()).isTrue();
    }

    @Test
    void passesNoOrgCandidateWhenOnlyPersonalMemoriesAreExtracted() {
        var request = captureRequest(memory(MemoryType.PERSONA, 95, Map.of("orgCandidate", true)));

        assertThat(request.orgCandidate()).isFalse();
    }

    private L1PipelineService.Request captureRequest(ExtractedMemory extracted) {
        var messages = List.of(message(101L));
        var conversation = conversation();
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.just(conversation));
        stubSetting();
        when(messageRepository.listMessages(CONVERSATION_ID, 0L, 10)).thenReturn(Flux.fromIterable(messages));
        when(extractionAi.extract(any(), eq(CONVERSATION_ID), eq(messages)))
                .thenReturn(Mono.just(new ExtractionOutcome(
                        List.of(new SceneSegment("偏好", List.of("101"), List.of(extracted))), "model-a")));
        when(memoryL1Repository.listByUser(
                        "team-a", "agent-a", "user-1", null, Long.MAX_VALUE, 100))
                .thenReturn(Flux.empty());
        when(conflictAi.judge(eq(""), anyList()))
                .thenReturn(Mono.just(List.of(DedupDecision.store(extracted.recordId()))));
        when(memoryL3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.empty());
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.empty());
        when(pipelineService.process(any(), any()))
                .thenReturn(Mono.just(new L1PipelineService.Result(List.of(), List.of(), 101L, 0, List.of())));
        when(conversationService.reducePending(conversation, 1)).thenReturn(Mono.just(1));

        StepVerifier.create(handler.handle(task())).verifyComplete();

        var captor = ArgumentCaptor.forClass(L1PipelineService.Request.class);
        verify(pipelineService).process(captor.capture(), any());
        return captor.getValue();
    }

    private void stubSetting() {
        var spec = new AiSetting().getSpec();
        spec.getMemory().setMaxMessagesPerExtraction(10);
        when(settingService.spec()).thenReturn(Mono.just(spec));
    }

    private static ExtractedMemory memory(MemoryType type, int priority, Map<String, Object> metadata) {
        return new ExtractedMemory("m-1", "可复用事实", type, priority, "偏好", List.of("101"), metadata);
    }

    private static AiTask task() {
        return new AiTask(
                TASK_ID,
                L1ExtractTaskHandler.TASK_TYPE,
                null,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                CONVERSATION_ID,
                0,
                Map.of("conversationId", CONVERSATION_ID),
                0,
                3,
                NOW,
                NOW,
                NOW);
    }

    private static Conversation conversation() {
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
                null,
                null,
                1,
                0L,
                NOW,
                NOW,
                NOW);
    }

    private static Message message(long id) {
        return new Message(
                id,
                CONVERSATION_ID,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                null,
                "req-1",
                1,
                0,
                "assistant",
                "内容",
                "TEXT",
                null,
                null,
                NOW);
    }
}
