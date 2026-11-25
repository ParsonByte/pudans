package cn.tolim.pudans.core.creation.task;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class AiEventStreams {

    private static final Duration EVENT_TTL = Duration.ofHours(24);

    private static final long EVENT_MAX_LENGTH = 1_000;

    private static final int REPLAY_LIMIT = 1_000;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ReactiveStringRedisTemplate redisTemplate;

    private final ReactiveRedisMessageListenerContainer listenerContainer;

    public AiEventStreams(
            ReactiveStringRedisTemplate redisTemplate,
            ReactiveRedisConnectionFactory connectionFactory) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = new ReactiveRedisMessageListenerContainer(connectionFactory);
    }

    public record StoredEvent(String eventId, String payload) {}

    public record PublishedEvent(String streamId, String eventId, String payload) {}

    // 每个事件流独立自增序号，便于客户端按序展示。
    public Mono<Long> nextSequence(String streamId) {
        var key = GenerationTaskKeys.eventSequence(streamId);
        return redisTemplate
                .opsForValue()
                .increment(key)
                .flatMap(sequence -> redisTemplate.expire(key, EVENT_TTL).thenReturn(sequence));
    }

    // 先写事件流再发布实时通知，保证重放优先于实时。
    public Mono<String> append(String streamId, String payload) {
        var streamKey = GenerationTaskKeys.eventStream(streamId);
        return redisTemplate
                .opsForStream()
                .add(
                        streamKey,
                        Map.of("payload", payload),
                        XAddOptions.maxlen(EVENT_MAX_LENGTH).approximateTrimming(true))
                .flatMap(id -> redisTemplate
                        .expire(streamKey, EVENT_TTL)
                        .then(publish(streamId, id.getValue(), payload))
                        .thenReturn(id.getValue()));
    }

    public Flux<StoredEvent> replay(String streamId, String afterEventId) {
        var streamKey = GenerationTaskKeys.eventStream(streamId);
        var range = afterEventId == null || afterEventId.isBlank()
                ? Range.<String>unbounded()
                : Range.from(Range.Bound.exclusive(afterEventId)).to(Range.Bound.unbounded());
        return redisTemplate
                .opsForStream()
                .range(streamKey, range, Limit.limit().count(REPLAY_LIMIT))
                .map(this::toStoredEvent);
    }

    // 任意实例都能通过 Pub/Sub 转发本实例写入的事件。
    public Flux<PublishedEvent> live() {
        return listenerContainer
                .receive(ChannelTopic.of(GenerationTaskKeys.EVENT_CHANNEL))
                .map(message -> parseEnvelope(message.getMessage()))
                .filter(Objects::nonNull);
    }

    @PreDestroy
    public void stop() {
        listenerContainer.destroyLater().subscribe();
    }

    // 只比较 Redis Stream ID 的毫秒与序号部分。
    public static boolean isAfter(String candidate, String reference) {
        if (reference == null || reference.isBlank()) {
            return true;
        }
        var left = parseId(candidate);
        var right = parseId(reference);
        if (left == null || right == null) {
            return false;
        }
        return left[0] > right[0] || (left[0] == right[0] && left[1] > right[1]);
    }

    public static boolean validId(String eventId) {
        return parseId(eventId) != null;
    }

    private Mono<Void> publish(String streamId, String eventId, String payload) {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("streamId", streamId);
        envelope.put("eventId", eventId);
        envelope.put("payload", payload);
        return redisTemplate
                .convertAndSend(GenerationTaskKeys.EVENT_CHANNEL, JSON.writeValueAsString(envelope))
                .then()
                .onErrorResume(error -> {
                    log.warn("发布 AI 事件实时通知失败：stream={}", streamId, error);
                    return Mono.empty();
                });
    }

    private StoredEvent toStoredEvent(MapRecord<String, Object, Object> record) {
        return new StoredEvent(
                record.getId().getValue(), stringValue(record.getValue().get("payload")));
    }

    private static PublishedEvent parseEnvelope(String json) {
        try {
            var value = JSON.readValue(json, Map.class);
            var streamId = stringValue(value.get("streamId"));
            var eventId = stringValue(value.get("eventId"));
            var payload = stringValue(value.get("payload"));
            if (streamId == null || eventId == null || payload == null) {
                return null;
            }
            return new PublishedEvent(streamId, eventId, payload);
        } catch (RuntimeException error) {
            log.warn("忽略无法解析的 AI 实时事件", error);
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static long[] parseId(String eventId) {
        if (eventId == null) {
            return null;
        }
        var parts = eventId.split("-", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
