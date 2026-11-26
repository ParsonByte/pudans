package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.MemoryJob;
import cn.tolim.pudans.contract.agent.dto.MemoryJobCreateRequest;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobItemRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
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
class MemoryJobsTest {

    private static final RequestIdentity IDENTITY =
            new RequestIdentity("owner-a", "workspace-a");

    @Mock
    MemoryJobRepository jobRepository;

    @Mock
    MemoryJobItemRepository items;

    @Mock
    AgentSessionRepository sessions;

    private MemoryJobs jobs;

    @BeforeEach
    void setUp() {
        jobs = new MemoryJobs(jobRepository, items, sessions);
    }

    @Test
    void returnsTheExistingConversationJobForTheSameSessionAndRun() {
        var sessionId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        var key = "session:" + sessionId + ":run:" + runId;
        var session = new AgentSessionEntity();
        session.setId(sessionId);
        session.setOwnerId(IDENTITY.ownerId());
        session.setWorkspaceId(IDENTITY.workspaceId());
        var existing = new MemoryJobEntity();
        existing.setId(UUID.randomUUID());
        existing.setJobType(MemoryJobs.CONVERSATION_EXTRACT);
        existing.setScopeType("owner");
        existing.setScopeId(IDENTITY.ownerId());
        existing.setStatus("running");
        existing.setIdempotencyKey(key);
        existing.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(sessions.findByIdAndOwnerIdAndWorkspaceId(
                        sessionId, IDENTITY.ownerId(), IDENTITY.workspaceId()))
                .thenReturn(Mono.just(session));
        when(jobRepository.findByIdempotencyKey(key)).thenReturn(Mono.just(existing));

        StepVerifier.create(jobs.create(
                        IDENTITY,
                        new MemoryJobCreateRequest(
                                MemoryJobs.CONVERSATION_EXTRACT, sessionId, runId)))
                .assertNext(job -> {
                    assertThat(job.id()).isEqualTo(existing.getId());
                    assertThat(job.status()).isEqualTo("running");
                    assertThat(job.idempotencyKey()).isEqualTo(key);
                })
                .verifyComplete();

        verify(jobRepository, never()).insert(any());
    }

    @Test
    void listsEveryMemoryJobForAdministrativeInspection() {
        var job = new MemoryJobEntity();
        job.setId(UUID.randomUUID());
        job.setJobType(MemoryJobs.POOL_CONSOLIDATE);
        job.setScopeType("global");
        job.setStatus("running");
        job.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(jobRepository.listAll(20)).thenReturn(Flux.just(job));

        var result = jobs.listAll(20).collectList().block();

        assertThat(result).extracting(MemoryJob::id).containsExactly(job.getId());
    }
}
