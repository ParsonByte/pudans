package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.core.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.task.AiTaskRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.task.AiTaskRepository.NewTask;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiModelTaskScheduler {

    public static final String TASK_TYPE = "AI_MODEL_POLL";

    private static final String DEFAULT_SPACE = AiTaskRepository.DEFAULT_SPACE;

    private static final long POLL_DELAY_SECONDS = 5;

    private final AiTaskRepository taskRepository;

    public Mono<Boolean> enqueuePoll(ModelRequestRow row) {
        return taskRepository.enqueue(new NewTask(
                TASK_TYPE,
                "ai-model-poll:" + row.requestId(),
                DEFAULT_SPACE,
                row.userId(),
                row.teamId(),
                row.agentId(),
                row.sessionId(),
                null,
                0,
                Map.of("requestId", row.requestId()),
                Instant.now().plusSeconds(POLL_DELAY_SECONDS),
                3));
    }

    public Mono<Boolean> reschedule(AiTask task) {
        return taskRepository.reschedule(
                task.id(), Instant.now().plusSeconds(POLL_DELAY_SECONDS));
    }
}
