package cn.tolim.pudans.contract.search.dto;

// Register a new search index
public record SearchIndexCreateRequest(String appCode,
                                       String indexCode,
                                       String name,
                                       String description,
                                       Boolean enabled) {
}