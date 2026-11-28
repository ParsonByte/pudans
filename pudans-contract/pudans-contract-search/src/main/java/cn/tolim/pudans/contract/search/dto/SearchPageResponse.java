package cn.tolim.pudans.contract.search.dto;

import java.util.List;

// Transport friendly search result page
public record SearchPageResponse<T>(List<T> items,
                                    int page,
                                    int size,
                                    long total,
                                    int totalPages,
                                    boolean hasNext) {
}