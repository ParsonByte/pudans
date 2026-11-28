package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.enums.SearchSort;

public record SearchDocumentCriteria(
        String appCode,
        String indexCode,
        String keyword,
        String ownerId,
        String orgId,
        SearchSort sort,
        int offset,
        int limit) {
}