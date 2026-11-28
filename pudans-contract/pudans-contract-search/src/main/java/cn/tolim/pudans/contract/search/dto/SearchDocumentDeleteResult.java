package cn.tolim.pudans.contract.search.dto;

// Result of deleting one document
public record SearchDocumentDeleteResult(String appCode,
                                         String indexCode,
                                         String documentId,
                                         boolean deleted) {
}