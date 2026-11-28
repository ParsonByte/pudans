package cn.tolim.pudans.audit.dto;

import java.util.List;

public record AuditPage<T>(List<T> items, int page, int size, long total, int totalPages) {

    // Convert a Spring Data page while exposing one-based page numbers
    public static <T> AuditPage<T> of(org.springframework.data.domain.Page<T> page) {
        return new AuditPage<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}