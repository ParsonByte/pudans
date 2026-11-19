package cn.tolim.pudans.core.ai.rag;

import org.jspecify.annotations.Nullable;

public record KnowledgeBaseIngestionRequest(
        String storageFileId,
        String fileName,
        @Nullable String mimeType,
        String checksum,
        @Nullable String uploadedBy,
        byte[] content) {

    public KnowledgeBaseIngestionRequest {
        if (storageFileId == null || storageFileId.isBlank()) {
            throw new IllegalArgumentException("存储文件标识不能为空。");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空。");
        }
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("文档校验和不能为空。");
        }
        if (content == null) {
            throw new IllegalArgumentException("文档内容不能为空。");
        }
    }
}
