package cn.tolim.pudans.core.agent.endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.MemoryJob;
import cn.tolim.pudans.core.agent.memory.MemoryAudit;
import cn.tolim.pudans.core.agent.memory.MemoryJobWorker;
import cn.tolim.pudans.core.agent.memory.MemoryJobs;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class MemoryJobEndpointTest {

    @Mock
    MemoryJobs jobs;

    @Mock
    MemoryJobWorker worker;

    @Mock
    MemoryAudit audit;

    @Mock
    ReactiveRequestIdentity identity;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new MemoryJobEndpoint(jobs, worker, audit, identity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsEveryMemoryJobForAdministrativeInspection() {
        var job = new MemoryJob(
                UUID.randomUUID(),
                MemoryJobs.POOL_CONSOLIDATE,
                "global",
                null,
                null,
                null,
                "completed",
                1,
                1,
                0,
                "daily:2026-11-26:pool:global",
                null,
                null,
                null,
                null,
                null);
        when(jobs.listAll(20)).thenReturn(Flux.just(job));

        client.get()
                .uri("/memoryjobs/-/all?limit=20")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].id")
                .isEqualTo(job.id().toString())
                .jsonPath("$.items[0].scopeType")
                .isEqualTo("global");

        verify(jobs).listAll(20);
    }
}
