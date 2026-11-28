package cn.tolim.pudans.contract.search.dto;

import java.time.LocalDateTime;
import java.util.List;

// One search hit
public record SearchHitResponse(String id,
                                String documentId,
                                String title,
                                String summary,
                                String snippet,
                                List<String> tags,
                                String ownerId,
                                String orgId,
                                LocalDateTime createTime,
                                LocalDateTime updateTime) {
}