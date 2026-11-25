package cn.tolim.pudans.core.creation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiTaskQueueTest {

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveStreamOperations<String, Object, Object> streamOperations;

    private AiTaskQueue queue;

    @BeforeEach
    void setUp() {
        queue = new AiTaskQueue(redisTemplate);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    void enqueuesOnlyTheTaskReferenceAndReason() {
        var taskId = UUID.randomUUID();
        when(streamOperations.add(eq(GenerationTaskKeys.TASK_QUEUE), any(Map.class), any(XAddOptions.class)))
                .thenReturn(Mono.just(RecordId.of("1-0")));

        StepVerifier.create(queue.enqueue(taskId, "retry"))
                .expectNext("1-0")
                .verifyComplete();

        var content = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(eq(GenerationTaskKeys.TASK_QUEUE), content.capture(), any(XAddOptions.class));
        assertThat(content.getValue())
                .containsEntry("taskId", taskId.toString())
                .containsEntry("reason", "retry");
    }

    @Test
    void writesRecoveryReasonWhenNoneIsProvided() {
        var taskId = UUID.randomUUID();
        when(streamOperations.add(eq(GenerationTaskKeys.TASK_QUEUE), any(Map.class), any(XAddOptions.class)))
                .thenReturn(Mono.just(RecordId.of("1-0")));

        queue.enqueue(taskId, null).block();

        var content = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(eq(GenerationTaskKeys.TASK_QUEUE), content.capture(), any(XAddOptions.class));
        assertThat(content.getValue()).containsEntry("reason", "created");
    }

    @Test
    void deadLettersAndAcknowledgesTheOriginalRecord() {
        var taskId = UUID.randomUUID();
        when(streamOperations.add(eq(GenerationTaskKeys.TASK_DEAD_QUEUE), any(Map.class), any(XAddOptions.class)))
                .thenReturn(Mono.just(RecordId.of("2-0")));
        when(redisTemplate.expire(eq(GenerationTaskKeys.TASK_DEAD_QUEUE), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(streamOperations.acknowledge(
                        eq(GenerationTaskKeys.TASK_QUEUE),
                        eq(GenerationTaskKeys.TASK_GROUP),
                        eq("1-0")))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(queue.deadLetter("1-0", taskId, "invalid"))
                .verifyComplete();

        verify(streamOperations).acknowledge(
                GenerationTaskKeys.TASK_QUEUE, GenerationTaskKeys.TASK_GROUP, "1-0");
    }
}
