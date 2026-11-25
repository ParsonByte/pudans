package cn.tolim.pudans.core.agent.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.LeaseResult;
import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryDailyConsolidationTest {

    @Mock
    MemoryDailyLease lease;

    @Mock
    MemoryJobs jobs;

    @Mock
    MemoryJobWorker worker;

    private MemoryDailyConsolidation consolidation;

    @BeforeEach
    void setUp() {
        consolidation = new MemoryDailyConsolidation(lease, jobs, worker);
    }

    @Test
    void abortsTheJobAndReleasesTheLeaseWhenTheHeartbeatIsLost() {
        var token = new TokenLease(
                "pudans:memory:daily:lock:global", "token-a", Duration.ofMinutes(30));
        var job = new MemoryJobEntity();
        job.setId(UUID.randomUUID());
        job.setJobType(MemoryJobs.POOL_CONSOLIDATE);
        job.setScopeType("global");
        job.setStatus("queued");
        job.setIdempotencyKey("daily:2026-01-01:pool:global");
        job.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(lease.acquire()).thenReturn(Mono.just(token));
        when(jobs.createDailyGlobal(any())).thenReturn(Mono.just(job));
        when(lease.heartbeats(token)).thenReturn(Flux.just(true, false));
        when(worker.process(job)).thenReturn(Mono.never());
        when(jobs.fail(
                        eq(job.getId()),
                        eq("memory-daily-lease-lost"),
                        anyString()))
                .thenReturn(Mono.empty());
        when(lease.release(token)).thenReturn(Mono.just(LeaseResult.APPLIED));

        StepVerifier.create(consolidation.runDaily()).verifyComplete();

        verify(jobs).fail(
                eq(job.getId()), eq("memory-daily-lease-lost"), anyString());
        verify(lease).release(token);
    }
}
