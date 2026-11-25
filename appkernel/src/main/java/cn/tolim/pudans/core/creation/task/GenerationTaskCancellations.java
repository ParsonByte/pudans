package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GenerationTaskCancellations {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final Duration ttl;

    public GenerationTaskCancellations(ReactiveStringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_TTL);
    }

    GenerationTaskCancellations(ReactiveStringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    // 直接任务与 Run 共用取消键前缀，作用域标识不同。
    public Mono<Void> markTask(UUID taskId) {
        return mark(taskId);
    }

    public Mono<Void> markRun(UUID runId) {
        return mark(runId);
    }

    public Mono<Boolean> cancelled(GenerationTask task) {
        var taskCancelled = redisTemplate.hasKey(GenerationTaskKeys.cancel(task.id()));
        if (task.runId() == null) {
            return taskCancelled;
        }
        return Mono.zip(taskCancelled, redisTemplate.hasKey(GenerationTaskKeys.cancel(task.runId())))
                .map(result -> result.getT1() || result.getT2());
    }

    // 记录短期取消标记，数据库状态仍是最终事实。
    private Mono<Void> mark(UUID scopeId) {
        return redisTemplate
                .opsForValue()
                .set(GenerationTaskKeys.cancel(scopeId), "1", ttl)
                .then();
    }
}
