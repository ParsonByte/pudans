package cn.tolim.pudans.core.ai.memory.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.task.MemoryTaskScheduler;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.MemoryRequestType;
import cn.tolim.pudans.core.ai.memory.governance.MemoryGovernance.SnapshotPayload;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.MemoryExportRow;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.MemoryRequestRecord;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.NewMemoryRequest;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.TaskStatusCount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryGovernanceTest {

    private static final String USER_ID = "user-1";

    private static final String ACTOR = "admin-1";

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    AiMemoryGovernanceRepository repository;

    @Mock
    AiSettings aiSettings;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiMessageRepository messageRepository;

    @Mock
    MemoryTaskScheduler memoryTaskScheduler;

    @InjectMocks
    MemoryGovernance service;

    @Test
    void flushesAConversationByEnqueuingExtraction() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.just(conversation()));
        when(messageRepository.findLatestId(7L)).thenReturn(Mono.just(21L));
        when(memoryTaskScheduler.enqueueExtraction(conversation(), USER_ID, 21L)).thenReturn(Mono.empty());

        StepVerifier.create(service.flush(7L, ACTOR)).verifyComplete();

        verify(memoryTaskScheduler).enqueueExtraction(conversation(), USER_ID, 21L);
    }

    @Test
    void flushRejectsAMissingConversation() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.empty());

        StepVerifier.create(service.flush(7L, ACTOR))
                .expectError(ApiException.class)
                .verify();
    }

    @Test
    void rebuildsL1WithTheLatestMessage() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.just(conversation()));
        when(messageRepository.findLatestId(7L)).thenReturn(Mono.just(21L));
        when(memoryTaskScheduler.enqueueExtraction(conversation(), ACTOR, 21L)).thenReturn(Mono.empty());

        StepVerifier.create(service.rebuild(7L, null, null, "L1", null, ACTOR))
                .assertNext(result -> {
                    assertThat(result.level()).isEqualTo("L1");
                    assertThat(result.queued()).isTrue();
                })
                .verifyComplete();

        verify(memoryTaskScheduler).enqueueExtraction(conversation(), ACTOR, 21L);
    }

    @Test
    void rebuildRejectsAMissingConversationId() {
        StepVerifier.create(service.rebuild(null, "team-a", "agent-a", "L2", 0L, ACTOR))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, MemoryGovernance.CODE_REQUEST_INVALID))
                .verify();
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void rebuildRejectsAnUnknownLevel() {
        StepVerifier.create(service.rebuild(7L, null, null, "L4", null, ACTOR))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, MemoryGovernance.CODE_REQUEST_INVALID))
                .verify();
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void rebuildRejectsAMissingConversation() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.empty());

        StepVerifier.create(service.rebuild(7L, null, null, "L2", null, ACTOR))
                .expectErrorSatisfies(error -> assertApiError(error, 404, "ai-conversation-not-found"))
                .verify();
    }

    @Test
    void rebuildsL2WithConversationScopeAndCursor() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.just(conversation()));
        when(memoryTaskScheduler.enqueueL2("team-a", "agent-a", 7L, ACTOR, 42L)).thenReturn(Mono.empty());

        StepVerifier.create(service.rebuild(7L, null, null, "L2", 42L, ACTOR))
                .assertNext(result -> {
                    assertThat(result.level()).isEqualTo("L2");
                    assertThat(result.queued()).isTrue();
                })
                .verifyComplete();

        verify(memoryTaskScheduler).enqueueL2("team-a", "agent-a", 7L, ACTOR, 42L);
    }

    @Test
    void rebuildsL3WithExplicitTeamAndAgent() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.just(conversation()));
        when(memoryTaskScheduler.enqueueL3("team-b", "agent-b", 7L, ACTOR, 0L)).thenReturn(Mono.empty());

        StepVerifier.create(service.rebuild(7L, "team-b", "agent-b", "L3", null, ACTOR))
                .assertNext(result -> {
                    assertThat(result.level()).isEqualTo("L3");
                    assertThat(result.queued()).isTrue();
                })
                .verifyComplete();

        verify(memoryTaskScheduler).enqueueL3("team-b", "agent-b", 7L, ACTOR, 0L);
    }

    @Test
    void exportsFilteredRecordsAsEscapedCsvLines() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        var row = new MemoryExportRow(
                1L,
                "record-1",
                "team-a",
                "agent-a",
                USER_ID,
                "scene,1",
                "WORK_FACT",
                50,
                "ACTIVE",
                "PRIVATE",
                2,
                new BigDecimal("95.50"),
                CREATED_AT,
                CREATED_AT,
                "He said \"hi\", then left\nnext line");
        when(repository.exportL1(
                        "team-a", "agent-a", USER_ID, MemoryType.WORK_FACT, MemoryStatus.ACTIVE, from, to, 5000))
                .thenReturn(Flux.just(row));

        StepVerifier.create(service.export(
                        "team-a", "agent-a", USER_ID, MemoryType.WORK_FACT, MemoryStatus.ACTIVE, from, to, 5000))
                .assertNext(header -> assertThat(header).isEqualTo(
                        "id,record_id,team_id,agent_id,user_id,scene_name,memory_type,priority,status,scope,"
                                + "version,decay_score,last_accessed_at,created_at,content\n"))
                .assertNext(line -> assertThat(line)
                        .startsWith("1,record-1,team-a,agent-a,user-1,\"scene,1\",WORK_FACT,50,"
                                + "ACTIVE,PRIVATE,2,95.50,")
                        .endsWith(",\"He said \"\"hi\"\", then left\nnext line\"\n"))
                .verifyComplete();
    }

    @Test
    void listsRecordsWithFilters() {
        when(repository.listL1("team-a", "agent-a", USER_ID, MemoryType.WORK_FACT, MemoryStatus.FROZEN, 9L, 25))
                .thenReturn(Flux.just(memory(3L, USER_ID, MemoryStatus.FROZEN)));

        StepVerifier.create(service.list(
                        "team-a", "agent-a", USER_ID, MemoryType.WORK_FACT, MemoryStatus.FROZEN, 9L, 25))
                .assertNext(records -> {
                    assertThat(records).hasSize(1);
                    assertThat(records.get(0).id()).isEqualTo(3L);
                    assertThat(records.get(0).status()).isEqualTo(MemoryStatus.FROZEN);
                })
                .verifyComplete();
    }

    @Test
    void returnsNotFoundForAMissingMemory() {
        when(repository.findL1ById(9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.get(9L))
                .expectErrorSatisfies(error -> assertApiError(error, 404, MemoryGovernance.CODE_NOT_FOUND))
                .verify();
    }

    @Test
    void freezesAnActiveMemory() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.ACTIVE)));
        when(repository.updateL1Status(1L, MemoryStatus.FROZEN))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.FROZEN)));

        StepVerifier.create(service.freeze(1L, ACTOR))
                .assertNext(record -> assertThat(record.status()).isEqualTo(MemoryStatus.FROZEN))
                .verifyComplete();
    }

    @Test
    void rejectsFreezingADeletedMemory() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.DELETED)));

        StepVerifier.create(service.freeze(1L, ACTOR))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, MemoryGovernance.CODE_REQUEST_INVALID))
                .verify();
        verify(repository, never()).updateL1Status(anyLong(), any(MemoryStatus.class));
    }

    @Test
    void unfreezesAFrozenMemory() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.FROZEN)));
        when(repository.updateL1Status(1L, MemoryStatus.ACTIVE))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.ACTIVE)));

        StepVerifier.create(service.unfreeze(1L, ACTOR))
                .assertNext(record -> assertThat(record.status()).isEqualTo(MemoryStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void deletesWithASnapshotAndLogicalStatus() {
        var memory = memory(1L, USER_ID, MemoryStatus.ACTIVE);
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory));
        when(aiSettings.spec()).thenReturn(Mono.just(settingSpec()));
        when(repository.insertSnapshot(eq(1L), eq(USER_ID), eq("team-a"), eq("agent-a"), anyString(), eq(180)))
                .thenReturn(Mono.just(10L));
        when(repository.updateL1Status(1L, MemoryStatus.DELETED))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.DELETED)));

        StepVerifier.create(service.delete(1L, "内容已过时", ACTOR))
                .assertNext(record -> assertThat(record.status()).isEqualTo(MemoryStatus.DELETED))
                .verifyComplete();

        var snapshot = ArgumentCaptor.forClass(String.class);
        verify(repository).insertSnapshot(eq(1L), eq(USER_ID), eq("team-a"), eq("agent-a"),
                snapshot.capture(), eq(180));
        assertThat(snapshot.getValue())
                .contains("\"content-1\"")
                .contains("\"actor\":\"admin-1\"")
                .contains("\"reason\":\"内容已过时\"");
    }

    @Test
    void rejectsDeletingWithoutAReason() {
        StepVerifier.create(service.delete(1L, " ", ACTOR))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, MemoryGovernance.CODE_REQUEST_INVALID))
                .verify();
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDeletingAnAlreadyDeletedMemory() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.DELETED)));

        StepVerifier.create(service.delete(1L, "重复删除", ACTOR))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 403, MemoryGovernance.CODE_DELETE_FORBIDDEN))
                .verify();
    }

    @Test
    void restoresContentAndMetadataFromTheLatestSnapshot() {
        var active = memory(1L, USER_ID, MemoryStatus.ACTIVE);
        var payload = new SnapshotPayload(active, ACTOR, "内容已过时", CREATED_AT);
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.DELETED)));
        when(repository.findLatestSnapshot(1L)).thenReturn(Mono.just(JsonUtils.objectToJson(payload)));
        when(repository.updateL1ContentAndStatus(1L, "content-1", active.metadata(), MemoryStatus.ACTIVE))
                .thenReturn(Mono.just(active));

        StepVerifier.create(service.restore(1L, ACTOR))
                .assertNext(record -> {
                    assertThat(record.status()).isEqualTo(MemoryStatus.ACTIVE);
                    assertThat(record.metadata()).containsEntry("source", "test");
                })
                .verifyComplete();
    }

    @Test
    void returnsNotFoundWhenNoSnapshotExists() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.DELETED)));
        when(repository.findLatestSnapshot(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.restore(1L, ACTOR))
                .expectErrorSatisfies(error -> assertApiError(error, 404, MemoryGovernance.CODE_NOT_FOUND))
                .verify();
    }

    @Test
    void createsARequestForAnOwnedMemory() {
        var memory = memory(1L, USER_ID, MemoryStatus.ACTIVE);
        var created = request(5L, 1L, USER_ID, "DELETE", "PENDING", "这条记忆不准确，需要删除", null);
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory));
        when(aiSettings.spec()).thenReturn(Mono.just(settingSpec()));
        when(repository.existsPendingRequest(USER_ID, "L1", 1L, "DELETE")).thenReturn(Mono.just(false));
        when(repository.insertRequest(any(NewMemoryRequest.class))).thenReturn(Mono.just(created));
        when(repository.updateL1Status(1L, MemoryStatus.PENDING_CORRECTION))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.PENDING_CORRECTION)));

        StepVerifier.create(service.createRequest(
                        USER_ID, "L1:1", MemoryRequestType.DELETE, "这条记忆不准确，需要删除", null))
                .assertNext(record -> assertThat(record.id()).isEqualTo(5L))
                .verifyComplete();

        var request = ArgumentCaptor.forClass(NewMemoryRequest.class);
        verify(repository).insertRequest(request.capture());
        assertThat(request.getValue().teamId()).isEqualTo("team-a");
        assertThat(request.getValue().agentId()).isEqualTo("agent-a");
        assertThat(request.getValue().memoryLevel()).isEqualTo("L1");
        assertThat(request.getValue().requestType()).isEqualTo("DELETE");
    }

    @Test
    void rejectsARequestForAnotherUsersMemory() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, "other-user", MemoryStatus.ACTIVE)));

        StepVerifier.create(service.createRequest(
                        USER_ID, "L1:1", MemoryRequestType.DELETE, "这条记忆不准确，需要删除", null))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 403, MemoryGovernance.CODE_FORBIDDEN))
                .verify();
    }

    @Test
    void rejectsAReasonShorterThanTheConfiguredMinimum() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.ACTIVE)));
        when(aiSettings.spec()).thenReturn(Mono.just(settingSpec()));

        StepVerifier.create(service.createRequest(USER_ID, "1", MemoryRequestType.DELETE, "太短", null))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, MemoryGovernance.CODE_REQUEST_INVALID))
                .verify();
        verify(repository, never()).existsPendingRequest(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void rejectsADuplicatePendingRequest() {
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.ACTIVE)));
        when(aiSettings.spec()).thenReturn(Mono.just(settingSpec()));
        when(repository.existsPendingRequest(USER_ID, "L1", 1L, "DELETE")).thenReturn(Mono.just(true));

        StepVerifier.create(service.createRequest(
                        USER_ID, "L1:1", MemoryRequestType.DELETE, "这条记忆不准确，需要删除", null))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 409, MemoryGovernance.CODE_REQUEST_CONFLICT))
                .verify();
        verify(repository, never()).insertRequest(any(NewMemoryRequest.class));
    }

    @Test
    void approvesADeleteRequest() {
        var pending = request(5L, 1L, USER_ID, "DELETE", "PENDING", "这条记忆不准确，需要删除", null);
        var approved = request(5L, 1L, USER_ID, "DELETE", "APPROVED", "这条记忆不准确，需要删除", null);
        when(repository.findRequestById(5L)).thenReturn(Mono.just(pending));
        when(repository.findL1ById(1L)).thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.ACTIVE)));
        when(aiSettings.spec()).thenReturn(Mono.just(settingSpec()));
        when(repository.insertSnapshot(eq(1L), eq(USER_ID), eq("team-a"), eq("agent-a"), anyString(), eq(180)))
                .thenReturn(Mono.just(10L));
        when(repository.updateL1Status(1L, MemoryStatus.DELETED))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.DELETED)));
        when(repository.updateRequestStatus(5L, "APPROVED", ACTOR, "确认删除"))
                .thenReturn(Mono.just(approved));

        StepVerifier.create(service.approve(5L, ACTOR, "确认删除"))
                .assertNext(record -> assertThat(record.status()).isEqualTo("APPROVED"))
                .verifyComplete();
    }

    @Test
    void approvesACorrectRequest() {
        var pending = request(
                5L, 1L, USER_ID, "CORRECT", "PENDING", "内容有误，需要更正", "修正后的内容");
        var approved = request(
                5L, 1L, USER_ID, "CORRECT", "APPROVED", "内容有误，需要更正", "修正后的内容");
        when(repository.findRequestById(5L)).thenReturn(Mono.just(pending));
        when(repository.updateL1ContentAndStatus(1L, "修正后的内容", null, MemoryStatus.ACTIVE))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.ACTIVE)));
        when(repository.updateRequestStatus(5L, "APPROVED", ACTOR, null)).thenReturn(Mono.just(approved));

        StepVerifier.create(service.approve(5L, ACTOR, null))
                .assertNext(record -> assertThat(record.status()).isEqualTo("APPROVED"))
                .verifyComplete();
        verify(repository).updateL1ContentAndStatus(1L, "修正后的内容", null, MemoryStatus.ACTIVE);
    }

    @Test
    void rejectsARequestAndReleasesTheCorrectionStatus() {
        var pending = request(
                5L, 1L, USER_ID, "CORRECT", "PENDING", "内容有误，需要更正", "修正后的内容");
        var rejected = request(
                5L, 1L, USER_ID, "CORRECT", "REJECTED", "内容有误，需要更正", "修正后的内容");
        when(repository.findRequestById(5L)).thenReturn(Mono.just(pending));
        when(repository.updateRequestStatus(5L, "REJECTED", ACTOR, "理由不充分"))
                .thenReturn(Mono.just(rejected));
        when(repository.findL1ById(1L))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.PENDING_CORRECTION)));
        when(repository.updateL1Status(1L, MemoryStatus.ACTIVE))
                .thenReturn(Mono.just(memory(1L, USER_ID, MemoryStatus.ACTIVE)));

        StepVerifier.create(service.reject(5L, ACTOR, "理由不充分"))
                .assertNext(record -> assertThat(record.status()).isEqualTo("REJECTED"))
                .verifyComplete();
        verify(repository).updateL1Status(1L, MemoryStatus.ACTIVE);
    }

    @Test
    void rejectsAReviewWithoutAComment() {
        StepVerifier.create(service.reject(5L, ACTOR, " "))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, MemoryGovernance.CODE_REQUEST_INVALID))
                .verify();
        verifyNoInteractions(repository);
    }

    @Test
    void aggregatesPipelineStats() {
        when(repository.taskStatusCounts()).thenReturn(Flux.just(
                new TaskStatusCount("L1_EXTRACT", 0, 3L),
                new TaskStatusCount("L1_EXTRACT", 2, 8L),
                new TaskStatusCount("L2_SCENE", 3, 1L),
                new TaskStatusCount("L2_SCENE", 9, 4L)));
        when(repository.sumConversationPendingCount()).thenReturn(Mono.just(4L));

        StepVerifier.create(service.pipelineStats())
                .assertNext(stats -> {
                    assertThat(stats.pendingTasks()).isEqualTo(3L);
                    assertThat(stats.runningTasks()).isZero();
                    assertThat(stats.doneTasks()).isEqualTo(8L);
                    assertThat(stats.failedTasks()).isEqualTo(1L);
                    assertThat(stats.pendingConversations()).isEqualTo(4L);
                    assertThat(stats.taskTypes()).containsExactly(
                            new MemoryGovernance.PipelineTaskType("L1_EXTRACT", 3L, 0L, 8L, 0L),
                            new MemoryGovernance.PipelineTaskType("L2_SCENE", 0L, 0L, 0L, 1L));
                })
                .verifyComplete();
    }

    @Test
    void parsesMemoryNamesAndRejectsUnknownPrefixes() {
        assertThat(MemoryGovernance.parseId("L1:12")).isEqualTo(12L);
        assertThat(MemoryGovernance.parseId("12")).isEqualTo(12L);
        assertThatThrownBy(() -> MemoryGovernance.parseId("L2:12"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("记忆标识格式不正确");
    }

    private static void assertApiError(Throwable error, int status, String code) {
        assertThat(error).isInstanceOf(ApiException.class);
        var api = (ApiException) error;
        assertThat(api.getStatusCode().value()).isEqualTo(status);
        assertThat(api.getCode()).isEqualTo(code);
    }

    private static L1MemoryRecord memory(long id, String userId, MemoryStatus status) {
        return new L1MemoryRecord(
                id,
                "record-" + id,
                new MemoryContext("team-a", userId, "agent-a", "session-1", null),
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

    private static MemoryRequestRecord request(
            long id,
            long memoryId,
            String userId,
            String type,
            String status,
            String reason,
            String suggestedContent) {
        return new MemoryRequestRecord(
                id,
                userId,
                "team-a",
                "agent-a",
                "L1",
                memoryId,
                type,
                reason,
                suggestedContent,
                status,
                null,
                null,
                null,
                CREATED_AT);
    }

    private static AiSetting.Spec settingSpec() {
        var spec = new AiSetting.Spec();
        spec.getMemory().setDeleteRetentionDays(180);
        spec.getMemory().setRequestMinReasonLength(10);
        return spec;
    }

    private static Conversation conversation() {
        return new Conversation(
                7L,
                "default",
                USER_ID,
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                null,
                "fast",
                "fast-group",
                "ACTIVE",
                null,
                null,
                0,
                0L,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT);
    }
}
