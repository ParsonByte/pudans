package cn.tolim.pudans.core.ai.memory.governance;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.MemoryRequestType;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.PipelineStats;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.PipelineTaskType;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.MemoryRequestRecord;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MemoryGovernanceEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    MemoryGovernance service;

    @Mock
    Accounts accounts;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new MemoryGovernanceEndpoint(service, new AiEndpointRequests(accounts));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build()))
                .build();
    }

    @Test
    void listsRecordsWithFilters() {
        when(service.list("team-a", "agent-a", "user-1", MemoryType.WORK_FACT, MemoryStatus.ACTIVE, 42L, 50))
                .thenReturn(Mono.just(List.of(memory(7L, MemoryStatus.ACTIVE))));

        client.get()
                .uri("/memoryrecords?teamId=team-a&agentId=agent-a&userId=user-1"
                        + "&type=WORK_FACT&status=ACTIVE&beforeId=42&limit=50")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(7)
                .jsonPath("$[0].status").isEqualTo("ACTIVE");
    }

    @Test
    void getsARecordByResourceName() {
        when(service.get(7L)).thenReturn(Mono.just(memory(7L, MemoryStatus.ACTIVE)));

        client.get()
                .uri("/memoryrecords/L1:7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7);
    }

    @Test
    void freezesARecordWithTheCurrentActor() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.freeze(7L, USER_ID)).thenReturn(Mono.just(memory(7L, MemoryStatus.FROZEN)));

        client.post()
                .uri("/memoryrecords/L1:7/freeze")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FROZEN");
    }

    @Test
    void deletesARecordWithTheQueryReason() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.delete(7L, "stale", USER_ID)).thenReturn(Mono.just(memory(7L, MemoryStatus.DELETED)));

        client.delete()
                .uri("/memoryrecords/7?reason=stale")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DELETED");

        verify(service).delete(7L, "stale", USER_ID);
    }

    @Test
    void submitsACorrectionRequest() {
        var request = request(5L, "PENDING");
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.createRequest(
                        USER_ID, "L1:7", MemoryRequestType.DELETE, "这条记忆不准确，需要删除", null))
                .thenReturn(Mono.just(request));

        client.post()
                .uri("/memories/L1:7/correction-request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MemoryGovernanceEndpoint.CorrectionRequest(
                        "DELETE", "这条记忆不准确，需要删除", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(5)
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void returnsForbiddenForAnotherUsersMemory() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.createRequest(
                        USER_ID, "L1:7", MemoryRequestType.DELETE, "这条记忆不准确，需要删除", null))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN,
                        MemoryGovernance.CODE_FORBIDDEN,
                        "只能对自己的记忆提交申请。")));

        client.post()
                .uri("/memories/L1:7/correction-request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MemoryGovernanceEndpoint.CorrectionRequest(
                        "DELETE", "这条记忆不准确，需要删除", null))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void returnsNotFoundForAMissingRecord() {
        when(service.get(9L)).thenReturn(Mono.error(new ApiException(
                HttpStatus.NOT_FOUND, MemoryGovernance.CODE_NOT_FOUND, "记忆不存在。")));

        client.get()
                .uri("/memoryrecords/9")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsAMalformedRecordName() {
        client.get()
                .uri("/memoryrecords/abc")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnUnknownRequestType() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));

        client.post()
                .uri("/memories/L1:7/correction-request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MemoryGovernanceEndpoint.CorrectionRequest("WRONG", "理由足够长足够长", null))
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void listsTheCurrentUsersRequests() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.listRequests(USER_ID, null, Long.MAX_VALUE, 20)).thenReturn(Flux.just(request(5L, "PENDING")));

        client.get()
                .uri("/memories/-/requests")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(5)
                .jsonPath("$[0].userId").isEqualTo(USER_ID);
    }

    @Test
    void approvesARequestWithAComment() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.approve(5L, USER_ID, "确认删除")).thenReturn(Mono.just(request(5L, "APPROVED")));

        client.post()
                .uri("/memoryrequests/5/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MemoryGovernanceEndpoint.ReviewRequest("确认删除"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED");
    }

    @Test
    void rejectsARequestWithoutAComment() {
        client.post()
                .uri("/memoryrequests/5/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MemoryGovernanceEndpoint.ReviewRequest(null))
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void returnsPipelineStats() {
        var stats = new PipelineStats(
                3L, 1L, 8L, 2L, 4L, List.of(new PipelineTaskType("L1_EXTRACT", 3L, 1L, 8L, 2L)));
        when(service.pipelineStats()).thenReturn(Mono.just(stats));

        client.get()
                .uri("/memoryrecords/-/pipeline")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pendingTasks").isEqualTo(3)
                .jsonPath("$.pendingConversations").isEqualTo(4)
                .jsonPath("$.taskTypes[0].taskType").isEqualTo("L1_EXTRACT");
    }

    @Test
    void rejectsAnOutOfRangeLimit() {
        client.get()
                .uri("/memoryrecords?limit=500")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void rebuildsMemoryRecordsAndReturnsAccepted() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.rebuild(7L, "team-a", "agent-a", "L2", 42L, USER_ID))
                .thenReturn(Mono.just(new MemoryGovernance.RebuildResult("L2", true)));

        client.post()
                .uri("/memoryrecords/-/rebuild")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MemoryGovernanceEndpoint.RebuildRequest(
                        7L, "team-a", "agent-a", "L2", 42L))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.level").isEqualTo("L2")
                .jsonPath("$.queued").isEqualTo(true);

        verify(service).rebuild(7L, "team-a", "agent-a", "L2", 42L, USER_ID);
    }

    @Test
    void rejectsARebuildWithAnInvalidLevel() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.rebuild(7L, null, null, "L4", null, USER_ID))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        MemoryGovernance.CODE_REQUEST_INVALID,
                        "重建层级不合法。")));

        client.post()
                .uri("/memoryrecords/-/rebuild")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MemoryGovernanceEndpoint.RebuildRequest(7L, null, null, "L4", null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void exportsMemoryRecordsAsACsvAttachment() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        when(service.export(
                        "team-a", "agent-a", "user-1", MemoryType.WORK_FACT, MemoryStatus.ACTIVE,
                        from, to, 5000))
                .thenReturn(Flux.just(MemoryCsvExporter.HEADER, "1,record-1\n"));

        client.get()
                .uri("/memoryrecords/-/export?teamId=team-a&agentId=agent-a&userId=user-1"
                        + "&type=WORK_FACT&status=ACTIVE"
                        + "&from=2026-01-01T00:00:00Z&to=2026-02-01T00:00:00Z")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv"))
                .expectHeader()
                .valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"memory-records.csv\"")
                .expectBody(String.class)
                .isEqualTo(MemoryCsvExporter.HEADER + "1,record-1\n");
    }

    private static User user() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        return user;
    }

    private static L1MemoryRecord memory(long id, MemoryStatus status) {
        return new L1MemoryRecord(
                id,
                "record-" + id,
                new MemoryContext("team-a", "user-1", "agent-a", "session-1", null),
                null,
                "content-" + id,
                MemoryType.WORK_FACT,
                50,
                "scene-1",
                List.of(),
                Map.of("source", "test"),
                "hash-" + id,
                MemoryScope.PRIVATE,
                false,
                status,
                1,
                List.of());
    }

    private static MemoryRequestRecord request(long id, String status) {
        return new MemoryRequestRecord(
                id,
                USER_ID,
                "team-a",
                "agent-a",
                "L1",
                7L,
                "DELETE",
                "这条记忆不准确，需要删除",
                null,
                status,
                null,
                null,
                null,
                CREATED_AT);
    }
}
