package cn.tolim.pudans.core.ai.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class L1Deduplication {

    public String normalize(String content) {
        Objects.requireNonNull(content, "content");
        var normalized = new StringBuilder(content.length());
        var pendingSpace = false;
        for (var index = 0; index < content.length(); index++) {
            var current = content.charAt(index);
            if (Character.isWhitespace(current)) {
                pendingSpace = normalized.length() > 0;
                continue;
            }
            if (pendingSpace) {
                normalized.append(' ');
                pendingSpace = false;
            }
            normalized.append(normalizeCharacter(current));
        }
        return normalized.toString();
    }

    public String dedupHash(MemoryContext context, String content) {
        Objects.requireNonNull(context, "context");
        var canonical = context.teamId() + context.agentId() + context.userId() + "\n" + normalize(content);
        return MemoryHashes.sha256Hex(canonical);
    }

    public FastDedupResult fastDedup(
            MemoryContext context, ExtractedMemory memory, Collection<ExistingMemory> existingMemories) {
        Objects.requireNonNull(memory, "memory");
        var hash = dedupHash(context, memory.content());
        var existing = existingMemories == null ? List.<ExistingMemory>of() : List.copyOf(existingMemories);
        var matches = existing.stream()
                .filter(candidate -> candidate.status().isDedupCandidate())
                .filter(candidate -> hash.equals(candidate.dedupHash()))
                .toList();
        return new FastDedupResult(hash, matches);
    }

    public List<DedupDecision> judgeConflicts(List<DedupCandidate> candidates, ConflictJudge judge) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(judge, "judge");
        if (candidates.isEmpty()) {
            return List.of();
        }
        var batch = List.copyOf(candidates);
        if (batch.stream().noneMatch(DedupCandidate::hasCandidates)) {
            return batch.stream().map(candidate -> DedupDecision.store(candidate.memory().recordId())).toList();
        }
        var judged = judge.judge(batch);
        var decisionsByRecord = new LinkedHashMap<String, DedupDecision>();
        if (judged != null) {
            for (var decision : judged) {
                if (decision != null) {
                    decisionsByRecord.putIfAbsent(decision.recordId(), decision);
                }
            }
        }
        var decisions = new ArrayList<DedupDecision>(batch.size());
        for (var candidate : batch) {
            var recordId = candidate.memory().recordId();
            decisions.add(decisionsByRecord.getOrDefault(recordId, DedupDecision.store(recordId)));
        }
        return List.copyOf(decisions);
    }

    private static char normalizeCharacter(char value) {
        var mapped = switch (value) {
            case '、' -> ',';
            case '。' -> '.';
            case '《', '『' -> '<';
            case '》', '』' -> '>';
            case '【' -> '[';
            case '】' -> ']';
            case '‘', '’' -> '\'';
            case '“', '”' -> '"';
            case '—' -> '-';
            default -> value >= '！' && value <= '～' ? (char) (value - 0xfee0) : value;
        };
        return mapped >= 'A' && mapped <= 'Z' ? (char) (mapped + ('a' - 'A')) : mapped;
    }

    @FunctionalInterface
    public interface ConflictJudge {
        List<DedupDecision> judge(List<DedupCandidate> candidates);
    }

    public record ExistingMemory(String recordId, String content, String dedupHash, MemoryStatus status) {

        public ExistingMemory {
            if (recordId == null || recordId.isBlank()) {
                throw new IllegalArgumentException("recordId 不能为空");
            }
            Objects.requireNonNull(content, "content");
            if (dedupHash == null || dedupHash.isBlank()) {
                throw new IllegalArgumentException("dedupHash 不能为空");
            }
            Objects.requireNonNull(status, "status");
        }
    }

    public record FastDedupResult(String dedupHash, List<ExistingMemory> matches) {

        public FastDedupResult {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }

        public boolean hasMatches() {
            return !matches.isEmpty();
        }
    }

    public record DedupCandidate(ExtractedMemory memory, List<ExistingMemory> candidates) {

        public DedupCandidate {
            Objects.requireNonNull(memory, "memory");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        public boolean hasCandidates() {
            return !candidates.isEmpty();
        }
    }
}
