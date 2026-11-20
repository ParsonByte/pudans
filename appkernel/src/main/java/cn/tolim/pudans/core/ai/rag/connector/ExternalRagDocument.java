package cn.tolim.pudans.core.ai.rag.connector;

import org.jspecify.annotations.Nullable;

public record ExternalRagDocument(
        @Nullable String documentId,
        @Nullable String documentName,
        @Nullable String chunkId,
        String content,
        double score,
        @Nullable Integer pageNo,
        @Nullable String headingPath) {

    public ExternalRagDocument {
        if (content == null) {
            throw new IllegalArgumentException("外部检索片段内容不能为空。");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("外部检索分数必须是有限数值。");
        }
    }
}