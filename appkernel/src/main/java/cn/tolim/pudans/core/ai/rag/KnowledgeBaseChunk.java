package cn.tolim.pudans.core.ai.rag;

import lombok.Data;

@Data
public class KnowledgeBaseChunk {

    private Long id;

    private String kbName;

    private Long documentId;

    private int chunkIndex;

    private String content;

    private int tokenCount;

    private Integer pageNo;

    private String headingPath;
}
