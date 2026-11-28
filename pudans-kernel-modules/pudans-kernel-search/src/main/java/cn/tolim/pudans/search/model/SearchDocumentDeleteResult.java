package cn.tolim.pudans.search.model;

public record SearchDocumentDeleteResult(
        String appCode,
        String indexCode,
        String documentId,
        boolean deleted) {
}