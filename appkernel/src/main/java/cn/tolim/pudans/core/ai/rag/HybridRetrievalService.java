package cn.tolim.pudans.core.ai.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HybridRetrievalService {

    private static final Comparator<RetrievalCandidate> BY_SCORE_DESC = Comparator
            .comparingDouble(RetrievalCandidate::score)
            .reversed()
            .thenComparingLong(RetrievalCandidate::chunkId);

    public List<RetrievedChunk> retrieve(
            List<RetrievalCandidate> vectorCandidates,
            List<RetrievalCandidate> keywordCandidates,
            RetrievalOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("检索参数不能为空。");
        }
        var lists = new ArrayList<List<RetrievalCandidate>>(2);
        addRankedList(lists, vectorCandidates);
        addRankedList(lists, keywordCandidates);
        if (lists.isEmpty()) {
            return List.of();
        }
        var fused = new LinkedHashMap<Long, FusedCandidate>();
        for (var candidates : lists) {
            for (var index = 0; index < candidates.size(); index++) {
                var candidate = candidates.get(index);
                var fusedCandidate = fused.computeIfAbsent(
                        candidate.chunkId(), chunkId -> new FusedCandidate(candidate));
                fusedCandidate.add(1.0 / (options.rrfK() + index + 1));
            }
        }
        // 归一化到 0 到 1，使跨列表的阈值判断与原始分数尺度无关
        var maximumScore = lists.size() / (double) (options.rrfK() + 1);
        var ranked = fused.values().stream()
                .map(candidate -> new RetrievedChunk(candidate.candidate, candidate.score / maximumScore))
                .filter(result -> result.score() >= options.scoreThreshold())
                .sorted(Comparator.comparingDouble(RetrievedChunk::score)
                        .reversed()
                        .thenComparingLong(result -> result.candidate().chunkId()))
                .limit(options.topK())
                .toList();
        return applyCharBudget(ranked, options.charBudget());
    }

    private static void addRankedList(
            List<List<RetrievalCandidate>> lists, List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        var byChunkId = new LinkedHashMap<Long, RetrievalCandidate>();
        for (var candidate : candidates) {
            byChunkId.merge(
                    candidate.chunkId(),
                    candidate,
                    (existing, replacement) -> replacement.score() > existing.score() ? replacement : existing);
        }
        var ranked = new ArrayList<>(byChunkId.values());
        ranked.sort(BY_SCORE_DESC);
        lists.add(ranked);
    }

    private static List<RetrievedChunk> applyCharBudget(List<RetrievedChunk> ranked, int charBudget) {
        var results = new ArrayList<RetrievedChunk>();
        var used = 0;
        for (var result : ranked) {
            var remaining = charBudget - used;
            if (remaining <= 0) {
                break;
            }
            var content = result.candidate().content();
            if (content.length() <= remaining) {
                results.add(result);
                used += content.length();
                continue;
            }
            var cut = remaining;
            if (Character.isHighSurrogate(content.charAt(cut - 1))) {
                cut--;
            }
            if (cut > 0) {
                results.add(new RetrievedChunk(
                        result.candidate().withContent(content.substring(0, cut)), result.score()));
            }
            break;
        }
        return List.copyOf(results);
    }

    private static final class FusedCandidate {

        private final RetrievalCandidate candidate;

        private double score;

        private FusedCandidate(RetrievalCandidate candidate) {
            this.candidate = candidate;
        }

        private void add(double value) {
            score += value;
        }
    }
}
