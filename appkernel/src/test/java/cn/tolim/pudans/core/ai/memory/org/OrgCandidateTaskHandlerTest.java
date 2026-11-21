package cn.tolim.pudans.core.ai.memory.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.L1Deduplication;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryHashes;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiOrgMemoryRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiOrgMemoryRepository.NewOrgMemory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OrgCandidateTaskHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static final long CONVERSATION_ID = 9L;

    @Mock
    AiMemoryL1Repository l1Repository;

    @Mock
    AiOrgMemoryRepository orgMemoryRepository;

    @Mock
    AiSettings settingService;

    private OrgCandidateTaskHandler handler;

    private L1Deduplication dedupService;

    @BeforeEach
    void setUp() {
        dedupService = new L1Deduplication();
        handler = new OrgCandidateTaskHandler(
                l1Repository, orgMemoryRepository, dedupService, settingService);
    }

    @Test
    void votesDeduplicatedCandidatesWithLineageAndPromotes() {
        givenDefaultSetting();
        when(l1Repository.findOrgCandidates("team-a", "agent-a", 42L, 100))
                .thenReturn(Flux.just(
                        record(11L, "Use Gradle", "user-1"),
                        record(12L, " use   GRADLE ", "user-2"),
                        record(13L, "use gradle", "user-1")));
        when(orgMemoryRepository.recordVote(any())).thenReturn(Mono.just(orgMemory(31L)));
        when(orgMemoryRepository.promoteIfEligible(31L, 3)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task(42L))).verifyComplete();

        var captor = ArgumentCaptor.forClass(NewOrgMemory.class);
        verify(orgMemoryRepository).recordVote(captor.capture());
        var candidate = captor.getValue();
        assertThat(candidate.content()).isEqualTo("Use Gradle");
        assertThat(candidate.contentType()).isEqualTo(MemoryType.WORK_METHOD.name());
        assertThat(candidate.dedupHash())
                .isEqualTo(MemoryHashes.sha256Hex(dedupService.normalize("Use Gradle")));
        assertThat(candidate.sourceTeamIds()).containsExactly("team-a");
        assertThat(candidate.sourceUserIds()).containsExactly("user-1", "user-2");
        assertThat(candidate.sourceMemoryIds()).containsExactly(11L, 12L, 13L);
        assertThat(candidate.crossVoteCount()).isEqualTo(2);
        verify(orgMemoryRepository).promoteIfEligible(31L, 3);
    }

    @Test
    void votesDistinctContentsSeparatelyAndPromotesEachGroup() {
        givenDefaultSetting();
        when(l1Repository.findOrgCandidates("team-a", "agent-a", 0L, 100))
                .thenReturn(Flux.just(
                        record(11L, "Use Gradle", "user-1"),
                        record(12L, "Always run tests", "user-1")));
        when(orgMemoryRepository.recordVote(any())).thenReturn(Mono.just(orgMemory(31L)));
        when(orgMemoryRepository.promoteIfEligible(anyLong(), eq(3))).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task(0L))).verifyComplete();

        verify(orgMemoryRepository, times(2)).recordVote(any());
        verify(orgMemoryRepository, times(2)).promoteIfEligible(anyLong(), eq(3));
    }

    @Test
    void usesTheConfiguredPromotionThreshold() {
        var spec = new AiSetting.Spec();
        spec.getMemory().setOrgPromotionThreshold(5);
        when(settingService.spec()).thenReturn(Mono.just(spec));
        when(l1Repository.findOrgCandidates("team-a", "agent-a", 42L, 100))
                .thenReturn(Flux.just(record(11L, "Use Gradle", "user-1")));
        when(orgMemoryRepository.recordVote(any())).thenReturn(Mono.just(orgMemory(31L)));
        when(orgMemoryRepository.promoteIfEligible(31L, 5)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task(42L))).verifyComplete();

        verify(orgMemoryRepository).promoteIfEligible(31L, 5);
    }

    @Test
    void completesWithoutVotesWhenNoCandidatesExist() {
        givenDefaultSetting();
        when(l1Repository.findOrgCandidates("team-a", "agent-a", 42L, 100)).thenReturn(Flux.empty());

        StepVerifier.create(handler.handle(task(42L))).verifyComplete();

        verifyNoInteractions(orgMemoryRepository);
    }

    @Test
    void readsTheWatermarkFromANumericPayload() {
        givenDefaultSetting();
        when(l1Repository.findOrgCandidates("team-a", "agent-a", 42L, 100)).thenReturn(Flux.empty());

        StepVerifier.create(handler.handle(task(42L))).verifyComplete();

        verify(l1Repository).findOrgCandidates("team-a", "agent-a", 42L, 100);
    }

    @Test
    void readsTheWatermarkFromATextPayload() {
        givenDefaultSetting();
        when(l1Repository.findOrgCandidates("team-a", "agent-a", 42L, 100)).thenReturn(Flux.empty());

        StepVerifier.create(handler.handle(task("42"))).verifyComplete();

        verify(l1Repository).findOrgCandidates("team-a", "agent-a", 42L, 100);
    }

    @Test
    void defaultsTheWatermarkWhenThePayloadOmitsIt() {
        givenDefaultSetting();
        when(l1Repository.findOrgCandidates("team-a", "agent-a", 0L, 100)).thenReturn(Flux.empty());

        StepVerifier.create(handler.handle(task(null))).verifyComplete();

        verify(l1Repository).findOrgCandidates("team-a", "agent-a", 0L, 100);
    }

    @Test
    void failsWhenTheTaskHasNoScope() {
        StepVerifier.create(handler.handle(task(null, null, Map.of())))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("团队"))
                .verify();

        verifyNoInteractions(l1Repository, orgMemoryRepository, settingService);
    }

    private void givenDefaultSetting() {
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
    }

    private static AiTask task(Object afterL1Id) {
        var payload = afterL1Id == null ? Map.<String, Object>of() : Map.of("afterL1Id", afterL1Id);
        return task("team-a", "agent-a", payload);
    }

    private static AiTask task(String teamId, String agentId, Map<String, Object> payload) {
        return new AiTask(
                5L,
                OrgCandidateTaskHandler.TASK_TYPE,
                "ORG:team-a:agent-a",
                "default",
                "user-1",
                teamId,
                agentId,
                "session-1",
                CONVERSATION_ID,
                0,
                payload,
                0,
                3,
                NOW,
                NOW,
                NOW);
    }

    private static L1MemoryRecord record(long id, String content, String userId) {
        return L1MemoryRecord.newRecord(
                        "m-" + id,
                        new MemoryContext("team-a", userId, "agent-a", "session-1", null),
                        CONVERSATION_ID,
                        content,
                        MemoryType.WORK_METHOD,
                        80,
                        "方法",
                        List.of(101L),
                        Map.of(),
                        "l1-hash-" + id,
                        MemoryScope.PRIVATE,
                        true,
                        1,
                        List.of(NOW))
                .withId(id);
    }

    private static OrgMemoryRecord orgMemory(long id) {
        return new OrgMemoryRecord(
                id,
                "Use Gradle",
                "WORK_METHOD",
                "hash-1",
                "PENDING",
                "SINGLE",
                List.of("team-a"),
                List.of("user-1"),
                List.of(11L),
                2,
                null,
                null,
                true,
                NOW,
                NOW);
    }
}
