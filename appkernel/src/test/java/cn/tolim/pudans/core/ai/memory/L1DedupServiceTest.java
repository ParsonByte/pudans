package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cn.tolim.pudans.core.ai.memory.L1DedupService.DedupCandidate;
import cn.tolim.pudans.core.ai.memory.L1DedupService.ExistingMemory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class L1DedupServiceTest {

    private final L1DedupService service = new L1DedupService();

    private final MemoryContext context = new MemoryContext("team-1", "user-1", "agent-1", "session-1", null);

    @Test
    void normalizesWhitespacePunctuationAndLatinCase() {
        assertThat(service.normalize("  Hello，　World！\n\tGood  bye。 "))
                .isEqualTo("hello, world! good bye.");
    }

    @Test
    void leavesNonLatinTextIntact() {
        assertThat(service.normalize("中文 ABC")).isEqualTo("中文 abc");
    }

    @Test
    void hashesNormalizedContentStably() {
        var first = service.dedupHash(context, "Hello， World！");
        var second = service.dedupHash(context, "  hello,  world!  ");

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void scopesHashByTeamAgentAndUserOnly() {
        var base = service.dedupHash(context, "same content");

        assertThat(service.dedupHash(
                        new MemoryContext("team-2", "user-1", "agent-1", "session-1", null), "same content"))
                .isNotEqualTo(base);
        assertThat(service.dedupHash(
                        new MemoryContext("team-1", "user-2", "agent-1", "session-1", null), "same content"))
                .isNotEqualTo(base);
        assertThat(service.dedupHash(
                        new MemoryContext("team-1", "user-1", "agent-2", "session-1", null), "same content"))
                .isNotEqualTo(base);
        assertThat(service.dedupHash(
                        new MemoryContext("team-1", "user-1", "agent-1", "session-2", "task-9"), "same content"))
                .isEqualTo(base);
    }

    @Test
    void fastDedupReturnsOnlyActiveAndSupersededHashMatches() {
        var memory = extracted("m-new", "Deploy， Friday");
        var hash = service.dedupHash(context, memory.content());
        var existing = List.of(
                new ExistingMemory("m-active", memory.content(), hash, MemoryStatus.ACTIVE),
                new ExistingMemory("m-superseded", memory.content(), hash, MemoryStatus.SUPERSEDED),
                new ExistingMemory("m-frozen", memory.content(), hash, MemoryStatus.FROZEN),
                new ExistingMemory("m-deleted", memory.content(), hash, MemoryStatus.DELETED),
                new ExistingMemory("m-other", "other", service.dedupHash(context, "other"), MemoryStatus.ACTIVE));

        var result = service.fastDedup(context, memory, existing);

        assertThat(result.hasMatches()).isTrue();
        assertThat(result.matches())
                .extracting(ExistingMemory::recordId)
                .containsExactly("m-active", "m-superseded");
    }

    @Test
    void storesEverythingWithoutCallingJudgeWhenNoCandidatesExist() {
        var calls = new AtomicInteger();
        L1DedupService.ConflictJudge judge = candidates -> {
            calls.incrementAndGet();
            return List.of();
        };

        var decisions = service.judgeConflicts(
                List.of(
                        new DedupCandidate(extracted("m1", "a"), List.of()),
                        new DedupCandidate(extracted("m2", "b"), List.of())),
                judge);

        assertThat(calls).hasValue(0);
        assertThat(decisions)
                .extracting(DedupDecision::recordId, DedupDecision::action)
                .containsExactly(tuple("m1", DedupAction.STORE), tuple("m2", DedupAction.STORE));
    }

    @Test
    void delegatesBatchOnceAndDefaultsMissingDecisionsToStore() {
        var candidate = new ExistingMemory("old-1", "a", service.dedupHash(context, "a"), MemoryStatus.ACTIVE);
        var batch = List.of(
                new DedupCandidate(extracted("m1", "a"), List.of(candidate)),
                new DedupCandidate(extracted("m2", "b"), List.of(candidate)));
        var calls = new AtomicInteger();
        L1DedupService.ConflictJudge judge = candidates -> {
            calls.incrementAndGet();
            assertThat(candidates).hasSize(2);
            return List.of(
                    new DedupDecision("m1", DedupAction.SKIP, List.of(), null, null, null, List.of()),
                    new DedupDecision("unknown", DedupAction.SKIP, List.of(), null, null, null, List.of()));
        };

        var decisions = service.judgeConflicts(batch, judge);

        assertThat(calls).hasValue(1);
        assertThat(decisions)
                .extracting(DedupDecision::recordId, DedupDecision::action)
                .containsExactly(tuple("m1", DedupAction.SKIP), tuple("m2", DedupAction.STORE));
    }

    private static ExtractedMemory extracted(String recordId, String content) {
        return new ExtractedMemory(
                recordId, content, MemoryType.EPISODIC, 50, "release", List.of("msg-1"), Map.of());
    }
}
