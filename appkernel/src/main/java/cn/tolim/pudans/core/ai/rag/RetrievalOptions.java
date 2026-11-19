package cn.tolim.pudans.core.ai.rag;

public record RetrievalOptions(int rrfK, double scoreThreshold, int topK, int charBudget) {

    public RetrievalOptions {
        if (rrfK < 1) {
            throw new IllegalArgumentException("RRF 常数必须大于 0。");
        }
        if (!Double.isFinite(scoreThreshold) || scoreThreshold < 0 || scoreThreshold > 1) {
            throw new IllegalArgumentException("分数阈值必须在 0 到 1 之间。");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("召回数量必须大于 0。");
        }
        if (charBudget < 1) {
            throw new IllegalArgumentException("字符预算必须大于 0。");
        }
    }
}
