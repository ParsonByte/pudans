package cn.tolim.pudans.contract.search.dto;

import java.util.List;
import java.util.Map;

// Insert or replace one document
public record SearchDocumentRequest(String documentId,
                                    String title,
                                    String summary,
                                    String content,
                                    List<String> tags,
                                    Map<String, Object> metadata,
                                    String ownerId,
                                    String orgId) {
}