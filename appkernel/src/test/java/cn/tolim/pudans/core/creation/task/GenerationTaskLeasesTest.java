package cn.tolim.pudans.core.creation.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.LeaseResult;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GenerationTaskLeasesTest {

    @Mock
    ReactiveRedisTokenLease leases;

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    private GenerationTaskLeases taskLeases;

    @BeforeEach
    void setUp() {
        taskLeases = new GenerationTaskLeases(
                leases, redisTemplate, Duration.ofSeconds(90), Duration.ofSeconds(30));
    }

    @Test
    void acquiresTheTaskScopedLease() {
        var taskId = UUID.randomUUID();
        var lease = new TokenLease(GenerationTaskKeys.lock(taskId), "token", Duration.ofSeconds(90));
        when(leases.acquire(eq(GenerationTaskKeys.lock(taskId)), any(Duration.class)))
                .thenReturn(Mono.just(lease));

        StepVerifier.create(taskLeases.acquire(taskId))
                .expectNext(lease)
                .verifyComplete();
    }

    @Test
    void renewsTheLeaseAndHeartbeatKey() {
        var taskId = UUID.randomUUID();
        var lease = new TokenLease(GenerationTaskKeys.lock(taskId), "token", Duration.ofSeconds(90));
        when(leases.renew(eq(lease), any(Duration.class))).thenReturn(Mono.just(LeaseResult.APPLIED));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(GenerationTaskKeys.heartbeat(taskId)), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(taskLeases.heartbeat(lease, "worker-1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void stopsTheHeartbeatWhenTheTokenIsLost() {
        var taskId = UUID.randomUUID();
        var lease = new TokenLease(GenerationTaskKeys.lock(taskId), "token", Duration.ofSeconds(90));
        when(leases.renew(eq(lease), any(Duration.class))).thenReturn(Mono.just(LeaseResult.LOST));

        StepVerifier.create(taskLeases.heartbeat(lease, "worker-1"))
                .expectNext(false)
                .verifyComplete();

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void deletesTheHeartbeatOnlyAfterAConfirmedRelease() {
        var taskId = UUID.randomUUID();
        var lease = new TokenLease(GenerationTaskKeys.lock(taskId), "token", Duration.ofSeconds(90));
        when(leases.release(lease)).thenReturn(Mono.just(LeaseResult.APPLIED));
        when(redisTemplate.delete(GenerationTaskKeys.heartbeat(taskId))).thenReturn(Mono.just(1L));

        StepVerifier.create(taskLeases.release(lease))
                .expectNext(LeaseResult.APPLIED)
                .verifyComplete();

        verify(redisTemplate).delete(GenerationTaskKeys.heartbeat(taskId));
    }

    @Test
    void doesNotDeleteAnotherOwnersHeartbeat() {
        var taskId = UUID.randomUUID();
        var lease = new TokenLease(GenerationTaskKeys.lock(taskId), "token", Duration.ofSeconds(90));
        when(leases.release(lease)).thenReturn(Mono.just(LeaseResult.LOST));

        StepVerifier.create(taskLeases.release(lease))
                .expectNext(LeaseResult.LOST)
                .verifyComplete();

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void reportsWhetherAWorkerHeartbeatExists() {
        var taskId = UUID.randomUUID();
        when(redisTemplate.hasKey(GenerationTaskKeys.heartbeat(taskId))).thenReturn(Mono.just(true));

        StepVerifier.create(taskLeases.alive(taskId))
                .expectNext(true)
                .verifyComplete();

        assertThat(GenerationTaskKeys.heartbeat(taskId))
                .isEqualTo("pudans:ai:task-heartbeat:" + taskId);
    }
}
