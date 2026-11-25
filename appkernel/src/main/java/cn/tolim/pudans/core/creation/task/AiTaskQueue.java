package cn.tolim.pudans.core.creation.task;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AiTaskQueue {

    private static final long TASK_MAX_LENGTH = 50_000;

    private static final long DEAD_MAX_LENGTH = 10_000;

    private static final Duration DEAD_TTL = Duration.ofDays(7);

    private final ReactiveStringRedisTemplate redisTemplate;

    public AiTaskQueue(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record Message(String recordId, UUID taskId) {}

    // 创建消费组；组已存在时按正常启动处理。
    public Mono<Void> ensureGroup() {
        return redisTemplate
                .opsForStream()
                .createGroup(
                        GenerationTaskKeys.TASK_QUEUE,
                        ReadOffset.from("0-0"),
                        GenerationTaskKeys.TASK_GROUP)
                .onErrorResume(AiTaskQueue::isBusyGroup, ignored -> Mono.empty())
                .then();
    }

    // 任务标识是唯一消息内容，业务事实以 PostgreSQL 为准。
    public Mono<String> enqueue(UUID taskId, String reason) {
        var content = new LinkedHashMap<String, String>();
        content.put("taskId", taskId.toString());
        content.put("reason", reason == null ? "created" : reason);
        content.put("enqueuedAt", Instant.now().toString());
        return redisTemplate
                .opsForStream()
                .add(
                        GenerationTaskKeys.TASK_QUEUE,
                        content,
                        XAddOptions.maxlen(TASK_MAX_LENGTH).approximateTrimming(true))
                .map(RecordId::getValue);
    }

    // 阻塞读取由 Redis 负责，线程不执行阻塞 I/O。
    public Flux<Message> read(String consumerName, int count, Duration blockTimeout) {
        return redisTemplate
                .opsForStream()
                .read(
                        Consumer.from(GenerationTaskKeys.TASK_GROUP, consumerName),
                        StreamReadOptions.empty().count(count).block(blockTimeout),
                        StreamOffset.create(GenerationTaskKeys.TASK_QUEUE, ReadOffset.lastConsumed()))
                .map(this::toMessage);
    }

    public Mono<Void> acknowledge(String recordId) {
        return redisTemplate
                .opsForStream()
                .acknowledge(
                        GenerationTaskKeys.TASK_QUEUE,
                        GenerationTaskKeys.TASK_GROUP,
                        recordId)
                .then();
    }

    // 死信保留原消息定位信息，随后确认主队列消息。
    public Mono<Void> deadLetter(String recordId, UUID taskId, String reason) {
        var content = new LinkedHashMap<String, String>();
        content.put("taskId", taskId == null ? "" : taskId.toString());
        content.put("queueRecordId", recordId);
        content.put("reason", reason == null ? "unknown" : reason);
        content.put("failedAt", Instant.now().toString());
        return redisTemplate
                .opsForStream()
                .add(
                        GenerationTaskKeys.TASK_DEAD_QUEUE,
                        content,
                        XAddOptions.maxlen(DEAD_MAX_LENGTH).approximateTrimming(true))
                .flatMap(ignored -> redisTemplate.expire(GenerationTaskKeys.TASK_DEAD_QUEUE, DEAD_TTL))
                .then(acknowledge(recordId));
    }

    // XCLAIM 重新分配超时 pending 消息，语义与 XAUTOCLAIM 一致。
    public Flux<Message> autoClaim(String consumerName, Duration minIdle, int count) {
        return redisTemplate
                .opsForStream()
                .pending(
                        GenerationTaskKeys.TASK_QUEUE,
                        GenerationTaskKeys.TASK_GROUP,
                        Range.unbounded(),
                        count)
                .flatMapMany(pending -> {
                    var ids = pending.stream()
                            .filter(message -> message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0)
                            .map(message -> message.getId())
                            .toArray(RecordId[]::new);
                    if (ids.length == 0) {
                        return Flux.empty();
                    }
                    return redisTemplate
                            .opsForStream()
                            .claim(
                                    GenerationTaskKeys.TASK_QUEUE,
                                    GenerationTaskKeys.TASK_GROUP,
                                    consumerName,
                                    minIdle,
                                    ids)
                            .map(this::toMessage);
                });
    }

    // 容忍无效旧消息，由消费者转入死信。
    private Message toMessage(MapRecord<String, Object, Object> record) {
        var value = record.getValue();
        UUID taskId = null;
        var text = stringValue(value.get("taskId"));
        if (text != null && !text.isBlank()) {
            try {
                taskId = UUID.fromString(text);
            } catch (IllegalArgumentException error) {
                log.warn("AI 任务消息包含无效任务标识：{}", record.getId().getValue());
            }
        }
        return new Message(record.getId().getValue(), taskId);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBusyGroup(Throwable error) {
        return hasMessage(error, "BUSYGROUP");
    }

    private static boolean hasMessage(Throwable error, String text) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}
