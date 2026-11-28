package cn.tolim.pudans.search.model;

import java.util.List;

public record SearchPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        int totalPages,
        boolean hasNext) {
}