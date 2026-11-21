package cn.tolim.pudans.core.ai.memory.task;

import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2Pipeline;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.MemoryGenerationInput;
import cn.tolim.pudans.core.ai.memory.MemoryPromptContext;
import cn.tolim.pudans.core.ai.memory.MemoryPromptInput;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.ai.memory.ai.SceneSegmentation;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.core.ai.task.AiTaskHandler;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class L2SceneTaskHandler implements AiTaskHandler {

    public static final String TASK_TYPE = PipelineTaskType.L2_SCENE.name();

    private final AiConversationRepository conversationRepository;

    private final AiMemoryL1Repository memoryL1Repository;

    private final AiMemoryL2Repository memoryL2Repository;

    private final MemoryPromptResolver promptResolver;

    private final SceneSegmentation sceneAi;

    private final L2Pipeline l2Pipeline;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Mono<Void> handle(AiTask task) {
        var conversationId = MemoryTaskPayloads.conversationId(task);
        if (conversationId == null) {
            return Mono.empty();
        }
        return conversationRepository
                .findById(conversationId)
                .flatMap(conversation -> process(task, conversation))
                .then();
    }

    private Mono<Void> process(AiTask task, Conversation conversation) {
        var teamId = MemoryTaskPayloads.teamId(task, conversation);
        var agentId = MemoryTaskPayloads.agentId(task, conversation);
        if (teamId == null || agentId == null) {
            return Mono.error(new IllegalArgumentException("L2 任务缺少团队或 Agent 标识"));
        }
        var afterL1Id = MemoryTaskPayloads.afterL1Id(task, conversation);
        var limit = MemoryTaskPayloads.batchLimit(task);
        return memoryL1Repository
                .findActiveByScope(teamId, agentId, afterL1Id, limit)
                .collectList()
                .flatMap(newL1 -> newL1.isEmpty()
                        ? Mono.<Void>empty()
                        : memoryL2Repository
                                .findByScope(teamId, agentId)
                                .collectList()
                                .flatMap(existing -> extract(
                                        teamId,
                                        agentId,
                                        conversation,
                                        afterL1Id,
                                        limit,
                                        newL1,
                                        existing)));
    }

    private Mono<Void> extract(
            String teamId,
            String agentId,
            Conversation conversation,
            long afterL1Id,
            int limit,
            List<L1MemoryRecord> newL1,
            List<L2SceneBlock> existingScenes) {
        var systemPrompt = MemoryPromptContext
                .compose(promptResolver, AiMemoryPrompt.Layer.L2, MemoryPromptInput.none())
                .systemPrompt();
        return sceneAi
                .extract(systemPrompt, newL1, existingScenes)
                .defaultIfEmpty(List.of())
                .flatMap(scenes -> l2Pipeline.process(
                        new L2Pipeline.Request(
                                teamId,
                                agentId,
                                conversation.id(),
                                afterL1Id,
                                limit,
                                MemoryPromptInput.none(),
                                MemoryGenerationInput.empty()),
                        (prompt, records, currentScenes) -> scenes))
                .then();
    }
}
