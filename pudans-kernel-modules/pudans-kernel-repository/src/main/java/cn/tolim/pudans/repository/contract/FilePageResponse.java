package cn.tolim.pudans.repository.contract;

import java.util.List;

public record FilePageResponse(List<FileMetadataResponse> items, long total, int page, int size,
                               int totalPages) {
}