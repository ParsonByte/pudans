package cn.tolim.pudans.contract.search.dto;

import java.time.LocalDateTime;

// One search index as returned by the API
public record SearchIndexResponse(String id,
                                  String appCode,
                                  String indexCode,
                                  String name,
                                  String description,
                                  boolean enabled,
                                  long documentCount,
                                  LocalDateTime createTime,
                                  LocalDateTime updateTime) {
}