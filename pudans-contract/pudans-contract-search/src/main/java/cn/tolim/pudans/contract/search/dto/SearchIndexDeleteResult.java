package cn.tolim.pudans.contract.search.dto;

// Result of deleting one index
public record SearchIndexDeleteResult(String appCode,
                                      String indexCode,
                                      boolean deleted,
                                      long deletedDocuments) {
}