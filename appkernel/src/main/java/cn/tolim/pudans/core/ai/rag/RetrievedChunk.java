package cn.tolim.pudans.core.ai.rag;

public record RetrievedChunk(RetrievalCandidate candidate, double score) {

    public RetrievedChunk {
        if (candidate == null) {
            throw new IllegalArgumentException("检索结果不能为空。");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("融合分数必须是有限数值。");
        }
    }
}
