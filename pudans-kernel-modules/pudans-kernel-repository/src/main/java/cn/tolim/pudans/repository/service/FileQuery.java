package cn.tolim.pudans.repository.service;

public record FileQuery(String tenantId, String appCode, String category, String keyword,
                        int page, int size) {
}