package cn.tolim.pudans.contract.search.dto;

import java.util.List;

// Insert or replace a batch of documents
public record SearchDocumentBatchRequest(List<SearchDocumentRequest> documents) {
}