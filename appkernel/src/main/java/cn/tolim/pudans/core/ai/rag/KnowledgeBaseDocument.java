package cn.tolim.pudans.core.ai.rag;

import java.time.Instant;
import lombok.Data;

@Data
public class KnowledgeBaseDocument {

    private Long id;

    private String kbName;

    private String storageFileId;

    private String fileName;

    private String mimeType;

    private Long fileSize;

    private String checksum;

    private DocumentStatus status = DocumentStatus.PENDING;

    private int chunkCount;

    private int tokenCount;

    private String errorMsg;

    private int version = 1;

    private String uploadedBy;

    private Instant createdAt;

    private Instant updatedAt;
}
