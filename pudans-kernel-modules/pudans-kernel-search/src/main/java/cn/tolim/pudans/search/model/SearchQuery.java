package cn.tolim.pudans.search.model;

public record SearchQuery(
        String appCode,
        String indexCode,
        String keyword,
        String ownerId,
        String orgId,
        Integer page,
        Integer size,
        String sort) {
}