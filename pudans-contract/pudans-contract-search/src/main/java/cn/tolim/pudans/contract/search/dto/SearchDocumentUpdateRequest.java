package cn.tolim.pudans.contract.search.dto;

import java.util.List;
import java.util.Map;

// Update an existing document
public record SearchDocumentUpdateRequest(String title,
                                          String summary,
                                          String content,
                                          List<String> tags,
                                          Map<String, Object> metadata,
                                          String ownerId,
                                          String orgId) {
}