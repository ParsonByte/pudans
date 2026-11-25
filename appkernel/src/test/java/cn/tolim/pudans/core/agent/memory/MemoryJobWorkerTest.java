package cn.tolim.pudans.core.agent.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddingModels;
import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddings;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobItemRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.vector.MemoryVectorRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryJobWorkerTest {

    @Mock
    MemoryJobRepository jobs;

    @Mock
    MemoryJobItemRepository jobItems;

    @Mock
    MemorySources sources;

    @Mock
    MemoryExtractionGateway extraction;

    @Mock
    PersonalMemories memories;

    @Mock
    MemoryEmbeddingModels embeddingModels;

    @Mock
    MemoryEmbeddings embeddings;

    @Mock
    MemoryVectorRepository vectors;

    @Mock
    MemoryEvents events;

    @Mock
    MemoryPooling pooling;

    @Mock
    MemoryAudit audit;

    private MemoryJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new MemoryJobWorker(
                jobs,
                jobItems,
                sources,
                extraction,
                memories,
                embeddingModels,
                embeddings,
                vectors,
                events,
                pooling,
                audit);
    }

    @Test
    void doesNotProcessAJobWhenAnotherWorkerHasAlreadyClaimedIt() {
        var job = new MemoryJobEntity();
        job.setId(UUID.randomUUID());
        job.setJobType(MemoryJobs.CONVERSATION_EXTRACT);
        job.setStatus("queued");
        job.setIdempotencyKey("session:" + UUID.randomUUID() + ":run:" + UUID.randomUUID());
        job.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(jobs.markRunning(eq(job.getId()), any())).thenReturn(Mono.just(false));

        StepVerifier.create(worker.process(job)).verifyComplete();

        verify(jobs).markRunning(eq(job.getId()), any());
        verifyNoInteractions(sources, extraction, memories, embeddingModels, embeddings, vectors);
    }
}
