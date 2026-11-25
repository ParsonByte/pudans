package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.core.creation.task.AiEventStreams;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryEvents {

    public static final String EXTRACTED = "memory.extracted";

    public static final String UPDATED = "memory.updated";

    public static final String POOL_CANDIDATE = "memory.pool.candidate";

    private final AiEventStreams streams;

    public Mono<Void> publish(
            RequestIdentity identity,
            String type,
            UUID memoryId,
            String layer,
            String status,
            Map<String, Object> data) {
        var eventData = new LinkedHashMap<String, Object>();
        if (data != null) {
            eventData.putAll(data);
        }
        eventData.put("memoryId", memoryId);
        eventData.put("layer", layer);
        eventData.put("status", status);
        eventData.put("ownerId", identity.ownerId());
        eventData.put("workspaceId", identity.workspaceId());
        return append("memory:" + identity.ownerId(), type, eventData, memoryId);
    }

    public Mono<Void> publishPoolCandidate(UUID memoryId, Map<String, Object> data) {
        var eventData = new LinkedHashMap<String, Object>();
        if (data != null) {
            eventData.putAll(data);
        }
        eventData.put("memoryId", memoryId);
        eventData.put("layer", "L4");
        eventData.put("status", "candidate");
        return append("memory:pool", POOL_CANDIDATE, eventData, memoryId);
    }

    private Mono<Void> append(
            String streamId, String type, Map<String, Object> eventData, UUID memoryId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", type);
        payload.put("data", eventData);
        payload.put("occurredAt", Instant.now().toString());
        return streams
                .nextSequence(streamId)
                .flatMap(sequence -> streams.append(
                        streamId, JsonUtils.objectToJson(withEventId(payload, sequence))))
                .onErrorResume(error -> {
                    log.warn("写入记忆事件失败：memory={} type={}", memoryId, type, error);
                    return Mono.empty();
                })
                .then();
    }

    private static Map<String, Object> withEventId(Map<String, Object> payload, long eventId) {
        var event = new LinkedHashMap<String, Object>();
        event.put("eventId", eventId);
        event.putAll(payload);
        return event;
    }
}
