package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.contract.agent.AgentRun;
import cn.tolim.pudans.contract.agent.dto.AgentRunEvent;
import cn.tolim.pudans.core.creation.task.AiEventStreams;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
public class AgentRunEvents {

    public static final String RUN_QUEUED = "run.queued";

    public static final String RUN_STARTED = "run.started";

    public static final String MESSAGE_START = "message.start";

    public static final String MESSAGE_DELTA = "message.delta";

    public static final String REASONING_DELTA = "reasoning.delta";

    public static final String TOOL_START = "tool.start";

    public static final String TOOL_RESULT = "tool.result";

    public static final String TOOL_APPROVAL_REQUIRED = "tool.approval_required";

    public static final String RUN_COMPLETED = "run.completed";

    public static final String RUN_FAILED = "run.failed";

    public static final String RUN_CANCELLED = "run.cancelled";

    public static final String PING = "ping";

    private static final Duration DEFAULT_PING_INTERVAL = Duration.ofSeconds(15);

    private static final Set<String> TERMINAL_TYPES =
            Set.of(RUN_COMPLETED, RUN_FAILED, RUN_CANCELLED);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiEventStreams streams;

    private final Duration pingInterval;

    private final Clock clock;

    @Autowired
    public AgentRunEvents(AiEventStreams streams) {
        this(streams, DEFAULT_PING_INTERVAL, Clock.systemUTC());
    }

    AgentRunEvents(AiEventStreams streams, Duration pingInterval, Clock clock) {
        this.streams = Objects.requireNonNull(streams, "streams");
        this.pingInterval = Objects.requireNonNull(pingInterval, "pingInterval");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // 复用现有 Redis Stream 与 Pub/Sub 写入 Run 事件。
    public Mono<Void> publish(
            UUID runId, UUID sessionId, String type, Map<String, Object> data) {
        var eventData = new LinkedHashMap<String, Object>();
        if (data != null) {
            eventData.putAll(data);
        }
        eventData.put("runId", runId);
        eventData.putIfAbsent("sessionId", sessionId);
        return streams
                .nextSequence(runId.toString())
                .flatMap(sequence -> {
                    var payload = new EventPayload(
                            sequence, runId, sessionId, type, eventData, now().toString());
                    return streams.append(runId.toString(), JSON.writeValueAsString(payload));
                })
                .onErrorResume(error -> {
                    log.warn("写入 Agent 运行事件失败：run={} type={}", runId, type, error);
                    return Mono.empty();
                })
                .then();
    }

    // 重放缺失事件并在事件过期时回退到真实运行终态。
    public Flux<AgentRunEvent> stream(AgentRun run, String lastEventId) {
        if (StringUtils.hasText(lastEventId) && !AiEventStreams.validId(lastEventId)) {
            return Flux.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "agent-event-id-invalid",
                    "Last-Event-ID 不是有效的 Redis Stream 事件标识。"));
        }
        var streamId = run.id().toString();
        Flux<AgentRunEvent> replay = streams
                .replay(streamId, lastEventId)
                .map(this::fromStored)
                .filter(Objects::nonNull)
                .collectList()
                .flatMapMany(replayed -> {
                    Flux<AgentRunEvent> prefix = Flux.fromIterable(replayed);
                    if (replayed.isEmpty()) {
                        return terminal(run)
                                ? Flux.concat(prefix, Mono.just(fallback(run)))
                                : prefix;
                    }
                    if (terminal(run)
                            && replayed.stream().noneMatch(event -> terminalType(event.type()))) {
                        return Flux.concat(prefix, Mono.just(fallback(run)));
                    }
                    return prefix;
                });
        Flux<AgentRunEvent> live = streams
                .live()
                .filter(event -> streamId.equals(event.streamId()))
                .filter(event -> AiEventStreams.isAfter(event.eventId(), lastEventId))
                .map(this::fromPublished)
                .filter(Objects::nonNull);
        Flux<AgentRunEvent> pings = Flux.interval(pingInterval).map(ignored -> ping(run));
        return Flux.merge(replay, live)
                .distinct(AgentRunEvent::eventId)
                .mergeWith(pings)
                .takeUntil(event -> terminalType(event.type()));
    }

    private AgentRunEvent fallback(AgentRun run) {
        var data = new LinkedHashMap<String, Object>();
        data.put("runId", run.id());
        data.put("sessionId", run.sessionId());
        data.put("status", run.status());
        data.put("fallback", true);
        if (run.errorCode() != null) {
            data.put("errorCode", run.errorCode());
        }
        if (run.errorMessage() != null) {
            data.put("errorMessage", run.errorMessage());
        }
        return new AgentRunEvent(
                fallbackEventId(run),
                0,
                run.id(),
                run.sessionId(),
                typeFor(run.status()),
                data,
                now());
    }

    private AgentRunEvent ping(AgentRun run) {
        return new AgentRunEvent(
                null,
                0,
                run.id(),
                run.sessionId(),
                PING,
                Map.of("at", now().toString()),
                now());
    }

    private AgentRunEvent fromStored(AiEventStreams.StoredEvent stored) {
        var payload = parsePayload(stored.payload());
        return payload == null ? null : toEvent(stored.eventId(), payload);
    }

    private AgentRunEvent fromPublished(AiEventStreams.PublishedEvent published) {
        var payload = parsePayload(published.payload());
        return payload == null ? null : toEvent(published.eventId(), payload);
    }

    private static AgentRunEvent toEvent(String eventId, EventPayload payload) {
        return new AgentRunEvent(
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
            log.warn("忽略无法解析的 Agent 运行事件", error);
            return null;
        }
    }

    private static String fallbackEventId(AgentRun run) {
        var version = run.finishedAt() == null ? run.createdAt() : run.finishedAt();
        return "fallback-" + run.id() + "-" + run.status() + "-" + version.toEpochMilli();
    }

    private static boolean terminal(AgentRun run) {
        return run.status() != null
                && (run.status().equals("completed")
                        || run.status().equals("failed")
                        || run.status().equals("cancelled"));
    }

    private static boolean terminalType(String type) {
        return TERMINAL_TYPES.contains(type);
    }

    private static String typeFor(String status) {
        return switch (status) {
            case "queued" -> RUN_QUEUED;
            case "completed" -> RUN_COMPLETED;
            case "failed" -> RUN_FAILED;
            case "cancelled" -> RUN_CANCELLED;
            default -> PING;
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
