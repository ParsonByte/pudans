package cn.tolim.pudans.search.model;

public record SearchIndexDeleteResult(
        String appCode,
        String indexCode,
        boolean deleted,
        long deletedDocuments) {
}