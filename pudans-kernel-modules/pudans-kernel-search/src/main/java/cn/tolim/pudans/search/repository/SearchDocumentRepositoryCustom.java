package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.entity.SearchDocumentEntity;

import java.util.List;

public interface SearchDocumentRepositoryCustom {

    // Execute the paginated PostgreSQL full-text query
    SearchDocumentPage search(SearchDocumentCriteria criteria);

    // Atomic insert-or-update on the document unique key
    int upsert(SearchDocumentEntity document);

    // Delete every document of one index
    int deleteByIndex(String appCode, String indexCode);

    // Delete one document, returning the number of removed rows
    int deleteDocument(String appCode, String indexCode, String documentId);

    // Count documents of one index
    long countByIndex(String appCode, String indexCode);

    // Count documents grouped by index for one application
    List<SearchIndexDocumentCount> countDocumentsByIndex(String appCode);
}