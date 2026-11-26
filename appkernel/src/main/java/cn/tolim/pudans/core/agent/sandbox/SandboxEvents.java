package cn.tolim.pudans.core.agent.sandbox;

import cn.tolim.pudans.contract.agent.Sandbox;
import cn.tolim.pudans.contract.agent.dto.SandboxEvent;
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
public class SandboxEvents {

    public static final String CREATED = "sandbox.created";

    public static final String READY = "sandbox.ready";

    public static final String OPERATION_STARTED = "sandbox.operation.started";

    public static final String OPERATION_OUTPUT = "sandbox.operation.output";

    public static final String OPERATION_FINISHED = "sandbox.operation.finished";

    public static final String SNAPSHOT_CREATED = "sandbox.snapshot.created";

    public static final String STOPPED = "sandbox.stopped";

    public static final String RESUMED = "sandbox.resumed";

    public static final String FAILED = "sandbox.failed";

    public static final String EXPIRED = "sandbox.expired";

    public static final String PING = "ping";

    private static final Duration DEFAULT_PING_INTERVAL = Duration.ofSeconds(15);

    private static final Set<String> TERMINAL_TYPES = Set.of(STOPPED, FAILED, EXPIRED);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiEventStreams streams;

    private final Duration pingInterval;

    private final Clock clock;

    @Autowired
    public SandboxEvents(AiEventStreams streams) {
        this(streams, DEFAULT_PING_INTERVAL, Clock.systemUTC());
    }

    SandboxEvents(AiEventStreams streams, Duration pingInterval, Clock clock) {
        this.streams = Objects.requireNonNull(streams, "streams");
        this.pingInterval = Objects.requireNonNull(pingInterval, "pingInterval");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // 沙箱事件复用 AI 事件流，独立流标识按沙箱划分。
    public Mono<Void> publish(UUID sandboxId, String type, Map<String, Object> data) {
        var eventData = new LinkedHashMap<String, Object>();
        if (data != null) {
            eventData.putAll(data);
        }
        eventData.put("sandboxId", sandboxId);
        return streams
                .nextSequence(sandboxId.toString())
                .flatMap(sequence -> {
                    var payload = new EventPayload(
                            sequence, sandboxId, type, eventData, now().toString());
                    return streams.append(sandboxId.toString(), JSON.writeValueAsString(payload));
                })
                .onErrorResume(error -> {
                    log.warn("写入沙箱事件失败：sandbox={} type={}", sandboxId, type, error);
                    return Mono.empty();
                })
                .then();
    }

    // 重放缺失事件，事件过期时使用数据库中的真实状态。
    public Flux<SandboxEvent> stream(Sandbox sandbox, String lastEventId) {
        if (StringUtils.hasText(lastEventId) && !AiEventStreams.validId(lastEventId)) {
            return Flux.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "sandbox-event-id-invalid",
                    "Last-Event-ID 不是有效的 Redis Stream 事件标识。"));
        }
        var streamId = sandbox.id().toString();
        Flux<SandboxEvent> replay = streams
                .replay(streamId, lastEventId)
                .map(this::fromStored)
                .filter(Objects::nonNull)
                .collectList()
                .flatMapMany(replayed -> {
                    Flux<SandboxEvent> prefix = Flux.fromIterable(replayed);
                    if (replayed.isEmpty()) {
                        return Flux.concat(prefix, Mono.just(fallback(sandbox)));
                    }
                    if (terminal(sandbox.status())
                            && replayed.stream().noneMatch(event -> terminalType(event.type()))) {
                        return Flux.concat(prefix, Mono.just(fallback(sandbox)));
                    }
                    return prefix;
                });
        Flux<SandboxEvent> live = streams
                .live()
                .filter(event -> streamId.equals(event.streamId()))
                .filter(event -> AiEventStreams.isAfter(event.eventId(), lastEventId))
                .map(this::fromPublished)
                .filter(Objects::nonNull);
        Flux<SandboxEvent> pings = Flux.interval(pingInterval).map(ignored -> ping(sandbox));
        return Flux.merge(replay, live)
                .distinct(SandboxEvent::eventId)
                .mergeWith(pings)
                .takeUntil(event -> terminalType(event.type()));
    }

    private SandboxEvent fallback(Sandbox sandbox) {
        var data = new LinkedHashMap<String, Object>();
        data.put("sandboxId", sandbox.id());
        data.put("status", sandbox.status().getCode());
        data.put("fallback", true);
        if (sandbox.errorCode() != null) {
            data.put("errorCode", sandbox.errorCode());
        }
        if (sandbox.errorMessage() != null) {
            data.put("errorMessage", sandbox.errorMessage());
        }
        return new SandboxEvent(
                fallbackEventId(sandbox),
                0,
                sandbox.id(),
                typeFor(sandbox.status()),
                data,
                now());
    }

    private SandboxEvent ping(Sandbox sandbox) {
        return new SandboxEvent(
                null,
                0,
                sandbox.id(),
                PING,
                Map.of("at", now().toString()),
                now());
    }

    private SandboxEvent fromStored(AiEventStreams.StoredEvent stored) {
        var payload = parsePayload(stored.payload());
        return payload == null ? null : toEvent(stored.eventId(), payload);
    }

    private SandboxEvent fromPublished(AiEventStreams.PublishedEvent published) {
        var payload = parsePayload(published.payload());
        return payload == null ? null : toEvent(published.eventId(), payload);
    }

    private static SandboxEvent toEvent(String eventId, EventPayload payload) {
        return new SandboxEvent(
                eventId,
                payload.seq(),
                payload.sandboxId(),
                payload.type(),
                payload.data(),
                Instant.parse(payload.createdAt()));
    }

    private static EventPayload parsePayload(String json) {
        try {
            return JSON.readValue(json, EventPayload.class);
        } catch (RuntimeException error) {
            log.warn("忽略无法解析的沙箱事件", error);
            return null;
        }
    }

    private static boolean terminal(Sandbox.Status status) {
        return status != null && status.terminal();
    }

    private static boolean terminalType(String type) {
        return TERMINAL_TYPES.contains(type);
    }

    private static String typeFor(Sandbox.Status status) {
        return switch (status) {
            case READY, RUNNING, SNAPSHOTTING, RESUMING -> READY;
            case STOPPED, CANCELLED -> STOPPED;
            case FAILED -> FAILED;
            case EXPIRED -> EXPIRED;
            default -> CREATED;
        };
    }

    private static String fallbackEventId(Sandbox sandbox) {
        var version = sandbox.stoppedAt() == null ? sandbox.createdAt() : sandbox.stoppedAt();
        return "fallback-" + sandbox.id() + "-" + sandbox.status().getCode() + "-"
                + version.toEpochMilli();
    }

    private Instant now() {
        return clock.instant();
    }

    private record EventPayload(
            long seq,
            UUID sandboxId,
            String type,
            Map<String, Object> data,
            String createdAt) {}
}
