package cn.tolim.pudans.core.ai.rag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeBaseQueryLog {

    private Long id;

    private String userId;

    private String requestId;

    private List<String> kbNames = new ArrayList<>();

    private String query;

    private List<Long> hitChunkIds = new ArrayList<>();

    private int hitCount;

    private BigDecimal topScore;

    private boolean rerankUsed;

    private Integer latencyMs;

    private Instant createdAt;
}
