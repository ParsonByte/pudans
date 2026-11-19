package cn.tolim.pudans.core.ai.rerank;

import java.util.List;

public record RerankResult(List<ScoredIndex> results) {

    public record ScoredIndex(int index, double score) {}
}
