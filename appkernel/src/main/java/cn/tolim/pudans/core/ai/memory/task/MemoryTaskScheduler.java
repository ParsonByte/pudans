package cn.tolim.pudans.core.ai.memory.task;

import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryPipelineTask;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryTaskRepository;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryTaskScheduler {

    private static final String REBUILD_SESSION = "rebuild";

    private final AiMemoryTaskRepository taskRepository;

    private final AiSettings aiSettings;

    public Mono<Void> enqueueExtraction(Conversation conversation, String userId, long lastMessageId) {
        return aiSettings
                .memoryEnabled()
                .flatMap(enabled -> Boolean.TRUE.equals(enabled)
                        ? enqueue(extractionTask(conversation, userId, lastMessageId))
                        : Mono.empty());
    }

    public Mono<Void> enqueueExtractionForImport(
            Conversation conversation, String userId, long lastMessageId) {
        return enqueue(extractionTask(conversation, userId, lastMessageId));
    }

    private static MemoryPipelineTask extractionTask(
            Conversation conversation, String userId, long lastMessageId) {
        return new MemoryPipelineTask(
                PipelineTaskType.L1_EXTRACT,
                "l1:" + conversation.id(),
                new MemoryContext(
                        conversation.teamId(),
                        userId,
                        conversation.agentId(),
                        conversation.sessionKey(),
                        null),
                Map.<String, Object>of(
                        "conversationId", conversation.id(), "lastMessageId", lastMessageId),
                new MemoryPipelineTask.Schedule(conversation.id(), 0, Instant.now()));
    }

    public Mono<Void> enqueueL2(
            String teamId, String agentId, long conversationId, String userId, long afterL1Id) {
        return enqueue(scopeTask(
                PipelineTaskType.L2_SCENE, "L2", teamId, agentId, conversationId, userId, afterL1Id));
    }

    public Mono<Void> enqueueL3(
            String teamId, String agentId, long conversationId, String userId, long afterL1Id) {
        return enqueue(scopeTask(
                PipelineTaskType.L3_PERSONA, "L3", teamId, agentId, conversationId, userId, afterL1Id));
    }

    private static MemoryPipelineTask scopeTask(
            PipelineTaskType taskType,
            String dedupPrefix,
            String teamId,
            String agentId,
            long conversationId,
            String userId,
            long afterL1Id) {
        return new MemoryPipelineTask(
                taskType,
                dedupPrefix + ":" + teamId + ":" + agentId,
                new MemoryContext(teamId, userId, agentId, REBUILD_SESSION, null),
                Map.of(
                        MemoryTaskPayloads.TEAM_ID, teamId,
                        MemoryTaskPayloads.AGENT_ID, agentId,
                        MemoryTaskPayloads.AFTER_L1_ID, afterL1Id),
                new MemoryPipelineTask.Schedule(conversationId, 0, Instant.now()));
    }

    private Mono<Void> enqueue(MemoryPipelineTask task) {
        return taskRepository
                .insertIfAbsent(task)
                .doOnError(error -> log.warn("{} 记忆任务投递失败。", task.taskType(), error))
                .onErrorResume(error -> Mono.empty())
                .then();
    }
}
