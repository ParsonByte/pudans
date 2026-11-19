package cn.tolim.pudans.core.ai.rag;

public record IngestionResult(KnowledgeBaseDocument document, boolean deduplicated) {

    public IngestionResult {
        if (document == null) {
            throw new IllegalArgumentException("入库结果不能为空。");
        }
    }
}
