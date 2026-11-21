package cn.tolim.pudans.core.live.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LiveRecordingLocksTest {

    private static final String LOCK_KEY = "pudans:live:recording-lock:source-1";

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> operations;

    private LiveRecordingLocks locks;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(operations);
        locks = new LiveRecordingLocks(redisTemplate);
    }

    @Test
    void runsTheActionAndReleasesTheOwnedLock() {
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object.class))).thenReturn(Flux.just(1L));

        var result = locks.withLock("source-1", Mono.just("recorded")).block();

        assertThat(result).isEqualTo("recorded");
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), any(Object.class));
    }

    @Test
    void rejectsWhenAnotherInstanceHoldsTheLock() {
        var executed = new AtomicBoolean();
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

        assertThatThrownBy(() -> locks
                        .withLock("source-1", Mono.fromRunnable(() -> executed.set(true)))
                        .block())
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LIVE_RECORDING_ACTIVE"));
        assertThat(executed).isFalse();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object.class));
    }

    @Test
    void releasesTheLockWhenTheActionFails() {
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object.class))).thenReturn(Flux.just(1L));

        assertThatThrownBy(() -> locks
                        .withLock("source-1", Mono.error(new IllegalStateException("boom")))
                        .block())
                .isInstanceOf(IllegalStateException.class);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), any(Object.class));
    }
}
