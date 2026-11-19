package cn.tolim.pudans.infra.r2dbc.ai.memory;

import cn.tolim.pudans.core.ai.memory.MemoryPipelineTask;
import cn.tolim.pudans.infra.r2dbc.ai.task.AiTaskRepository;
import cn.tolim.pudans.infra.r2dbc.ai.task.AiTaskRepository.NewTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryTaskRepository {

    private final AiTaskRepository taskRepository;

    public Mono<Boolean> insertIfAbsent(MemoryPipelineTask task) {
        var schedule = task.schedule();
        var context = task.context();
        return taskRepository.enqueue(new NewTask(
                task.taskType().name(),
                task.dedupKey(),
                AiTaskRepository.DEFAULT_SPACE,
                context.userId(),
                context.teamId(),
                context.agentId(),
                context.sessionId(),
                schedule == null ? null : schedule.conversationId(),
                schedule == null ? 0 : schedule.priority(),
                task.payload(),
                schedule == null ? null : schedule.nextRunAt(),
                3));
    }
}
