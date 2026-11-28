package cn.tolim.pudans.contract.mq.dto;

import java.util.List;

// Transport friendly page wrapper
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages) {
}