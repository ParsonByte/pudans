package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskEvent;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class GenerationTaskEvents {

    public static final String TASK_QUEUED = "task.queued";

    public static final String TASK_PROGRESS = "task.progress";

    public static final String TASK_RESULT = "task.result";

    public static final String TASK_FAILED = "task.failed";

    public static final String TASK_CANCELLED = "task.cancelled";

    public static final String PING = "ping";

    private static final Duration DEFAULT_PING_INTERVAL = Duration.ofSeconds(15);

    private static final Set<String> TERMINAL_TYPES = Set.of(TASK_RESULT, TASK_FAILED, TASK_CANCELLED);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiEventStreams streams;

    private final Duration pingInterval;

    private final Clock clock;

    @Autowired
    public GenerationTaskEvents(AiEventStreams streams) {
        this(streams, DEFAULT_PING_INTERVAL, Clock.systemUTC());
    }

    GenerationTaskEvents(AiEventStreams streams, Duration pingInterval, Clock clock) {
        this.streams = Objects.requireNonNull(streams, "streams");
        this.pingInterval = Objects.requireNonNull(pingInterval, "pingInterval");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // 任务属于 Run 时写入 Run 事件流，直接任务回退任务事件流。
    public static String streamId(GenerationTask task) {
        return task.runId() == null ? task.id().toString() : task.runId().toString();
    }

    public Mono<GenerationTaskEvent> publish(
            GenerationTask task, String type, Map<String, Object> data) {
        var streamId = streamId(task);
        return streams
                .nextSequence(streamId)
                .flatMap(sequence -> {
                    var payload = new EventPayload(
                            sequence,
                            task.runId(),
                            task.sessionId(),
                            type,
                            data == null ? Map.of() : data,
                            now().toString());
                    return streams
                            .append(streamId, JSON.writeValueAsString(payload))
                            .map(eventId -> toEvent(eventId, payload));
                })
                .onErrorResume(error -> {
                    log.warn("写入生成任务事件失败：task={} type={}", task.id(), type, error);
                    return Mono.empty();
                });
    }

    // 从 Redis Stream 重放缺失事件，过期或为空时返回真实最终状态。
    public Flux<GenerationTaskEvent> stream(GenerationTask task, String lastEventId) {
        if (StringUtils.hasText(lastEventId) && !AiEventStreams.validId(lastEventId)) {
            return Flux.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "generation-event-id-invalid",
                    "Last-Event-ID 不是有效的 Redis Stream 事件标识。"));
        }
        var streamId = streamId(task);
        Flux<GenerationTaskEvent> replay = streams
                .replay(streamId, lastEventId)
                .map(this::fromStored)
                .filter(Objects::nonNull)
                .collectList()
                .flatMapMany(replayed -> {
                    Flux<GenerationTaskEvent> prefix = Flux.fromIterable(replayed);
                    if (replayed.isEmpty()) {
                        return Flux.concat(prefix, Mono.just(fallback(task)));
                    }
                    if (task.terminal() && replayed.stream().noneMatch(event -> terminalType(event.type()))) {
                        return Flux.concat(prefix, Mono.just(fallback(task)));
                    }
                    return prefix;
                });
        Flux<GenerationTaskEvent> live = streams
                .live()
                .filter(event -> streamId.equals(event.streamId()))
                .filter(event -> AiEventStreams.isAfter(event.eventId(), lastEventId))
                .map(this::fromPublished)
                .filter(Objects::nonNull);
        Flux<GenerationTaskEvent> merged = Flux.merge(replay, live)
                .distinct(GenerationTaskEvent::eventId);
        Flux<GenerationTaskEvent> pings = Flux.interval(pingInterval).map(ignored -> ping(task));
        return merged
                .mergeWith(pings)
                .takeUntil(event -> terminalType(event.type()));
    }

    private String fallbackEventId(GenerationTask task) {
        var version = task.finishedAt() == null ? task.createdAt() : task.finishedAt();
        return "fallback-" + task.id() + "-" + task.status().getCode() + "-" + version.toEpochMilli();
    }

    // 不伪造中间事件，只发送数据库中的当前真实状态。
    private GenerationTaskEvent fallback(GenerationTask task) {
        var data = new LinkedHashMap<String, Object>();
        data.put("taskId", task.id());
        data.put("status", task.status().getCode());
        data.put("fallback", true);
        data.put("resultAssetIds", task.resultAssetIds());
        if (task.errorCode() != null) {
            data.put("errorCode", task.errorCode());
        }
        if (task.errorMessage() != null) {
            data.put("errorMessage", task.errorMessage());
        }
        return new GenerationTaskEvent(
                fallbackEventId(task),
                0,
                task.runId(),
                task.sessionId(),
                typeFor(task.status()),
                data,
                now());
    }

    private GenerationTaskEvent ping(GenerationTask task) {
        return new GenerationTaskEvent(
                null,
                0,
                task.runId(),
                task.sessionId(),
                PING,
                Map.of("at", now().toString()),
                now());
    }

    private GenerationTaskEvent fromStored(AiEventStreams.StoredEvent stored) {
        var payload = parsePayload(stored.payload());
        return payload == null ? null : toEvent(stored.eventId(), payload);
    }

    private GenerationTaskEvent fromPublished(AiEventStreams.PublishedEvent published) {
        var payload = parsePayload(published.payload());
        return payload == null ? null : toEvent(published.eventId(), payload);
    }

    private static GenerationTaskEvent toEvent(String eventId, EventPayload payload) {
        return new GenerationTaskEvent(
                eventId,
                payload.seq(),
                payload.runId(),
                payload.sessionId(),
                payload.type(),
                payload.data(),
                Instant.parse(payload.createdAt()));
    }

    private static EventPayload parsePayload(String json) {
        try {
            return JSON.readValue(json, EventPayload.class);
        } catch (RuntimeException error) {
            log.warn("忽略无法解析的生成任务事件", error);
            return null;
        }
    }

    private static boolean terminalType(String type) {
        return TERMINAL_TYPES.contains(type);
    }

    private static String typeFor(GenerationTask.Status status) {
        return switch (status) {
            case SUCCEEDED -> TASK_RESULT;
            case FAILED -> TASK_FAILED;
            case CANCELLED -> TASK_CANCELLED;
            default -> TASK_PROGRESS;
        };
    }

    private Instant now() {
        return clock.instant();
    }

    private record EventPayload(
            long seq,
            UUID runId,
            UUID sessionId,
            String type,
            Map<String, Object> data,
            String createdAt) {}
}
