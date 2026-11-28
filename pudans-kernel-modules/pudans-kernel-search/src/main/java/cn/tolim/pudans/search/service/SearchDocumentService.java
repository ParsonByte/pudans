package cn.tolim.pudans.search.service;

import cn.tolim.pudans.search.config.SearchProperties;
import cn.tolim.pudans.search.constant.SearchConstants;
import cn.tolim.pudans.search.entity.SearchDocumentEntity;
import cn.tolim.pudans.search.error.SearchErrorCode;
import cn.tolim.pudans.search.error.SearchException;
import cn.tolim.pudans.search.model.BatchIndexResult;
import cn.tolim.pudans.search.model.SearchDocumentBatchRequest;
import cn.tolim.pudans.search.model.SearchDocumentDeleteResult;
import cn.tolim.pudans.search.model.SearchDocumentRequest;
import cn.tolim.pudans.search.model.SearchDocumentResponse;
import cn.tolim.pudans.search.model.SearchDocumentUpdateRequest;
import cn.tolim.pudans.search.repository.SearchDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchDocumentService {

    private final SearchDocumentRepository documentRepository;
    private final SearchIndexService indexService;
    private final SearchProperties properties;

    public SearchDocumentService(SearchDocumentRepository documentRepository,
                                 SearchIndexService indexService,
                                 SearchProperties properties) {
        this.documentRepository = documentRepository;
        this.indexService = indexService;
        this.properties = properties;
    }

    // Insert or replace one document, keyed by appCode/indexCode/documentId
    @Transactional
    public SearchDocumentResponse indexDocument(String appCode, String indexCode,
                                                 SearchDocumentRequest request) {
        String normalizedAppCode = SearchIndexService.requireText(appCode,
                SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = SearchIndexService.requireText(indexCode,
                SearchErrorCode.INVALID_INDEX, "indexCode");
        validateDocument(request.documentId(), request.title(), request.content());
        validateTags(request.tags());
        indexService.requireEnabledIndex(normalizedAppCode, normalizedIndexCode);
        SearchDocumentEntity entity = buildEntity(normalizedAppCode, normalizedIndexCode,
                request, LocalDateTime.now());
        documentRepository.upsert(entity);
        return toResponse(requireDocument(normalizedAppCode, normalizedIndexCode,
                entity.getDocumentId()));
    }

    // Update an existing document; the document id is immutable
    @Transactional
    public SearchDocumentResponse updateDocument(String appCode, String indexCode, String documentId,
                                                 SearchDocumentUpdateRequest request) {
        String normalizedAppCode = SearchIndexService.requireText(appCode,
                SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = SearchIndexService.requireText(indexCode,
                SearchErrorCode.INVALID_INDEX, "indexCode");
        String normalizedDocumentId = SearchIndexService.requireText(documentId,
                SearchErrorCode.INVALID_DOCUMENT, "documentId");
        indexService.requireEnabledIndex(normalizedAppCode, normalizedIndexCode);
        validateDocument(normalizedDocumentId, request.title(), request.content());
        validateTags(request.tags());
        SearchDocumentEntity entity = requireDocument(normalizedAppCode, normalizedIndexCode,
                normalizedDocumentId);
        entity.setTitle(request.title());
        entity.setSummary(request.summary());
        entity.setContent(request.content());
        entity.setTags(normalizeTags(request.tags()));
        entity.setMetadata(normalizeMetadata(request.metadata()));
        entity.setOwnerId(SearchIndexService.trimToNull(request.ownerId()));
        entity.setOrgId(SearchIndexService.trimToNull(request.orgId()));
        entity.setUpdateTime(LocalDateTime.now());
        return toResponse(documentRepository.save(entity));
    }

    // Load one document with its full content
    @Transactional(readOnly = true)
    public SearchDocumentResponse getDocument(String appCode, String indexCode, String documentId) {
        String normalizedAppCode = SearchIndexService.requireText(appCode,
                SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = SearchIndexService.requireText(indexCode,
                SearchErrorCode.INVALID_INDEX, "indexCode");
        String normalizedDocumentId = SearchIndexService.requireText(documentId,
                SearchErrorCode.INVALID_DOCUMENT, "documentId");
        return toResponse(requireDocument(normalizedAppCode, normalizedIndexCode, normalizedDocumentId));
    }

    // Delete one document; deleting a missing document is not an error
    @Transactional
    public SearchDocumentDeleteResult deleteDocument(String appCode, String indexCode, String documentId) {
        String normalizedAppCode = SearchIndexService.requireText(appCode,
                SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = SearchIndexService.requireText(indexCode,
                SearchErrorCode.INVALID_INDEX, "indexCode");
        String normalizedDocumentId = SearchIndexService.requireText(documentId,
                SearchErrorCode.INVALID_DOCUMENT, "documentId");
        int deleted = documentRepository.deleteDocument(normalizedAppCode, normalizedIndexCode,
                normalizedDocumentId);
        return new SearchDocumentDeleteResult(normalizedAppCode, normalizedIndexCode,
                normalizedDocumentId, deleted > 0);
    }

    // Insert or replace a batch of documents in one transaction
    @Transactional
    public BatchIndexResult indexDocuments(String appCode, String indexCode,
                                           SearchDocumentBatchRequest request) {
        String normalizedAppCode = SearchIndexService.requireText(appCode,
                SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = SearchIndexService.requireText(indexCode,
                SearchErrorCode.INVALID_INDEX, "indexCode");
        List<SearchDocumentRequest> documents = request.documents();
        if (documents == null || documents.isEmpty()) {
            throw new SearchException(SearchErrorCode.INVALID_DOCUMENT,
                    "At least one document is required");
        }
        if (documents.size() > properties.getMaxBatchSize()) {
            throw new SearchException(SearchErrorCode.BATCH_TOO_LARGE,
                    "A batch may contain at most " + properties.getMaxBatchSize() + " documents");
        }
        // Validate the whole batch before touching the index or the database
        for (SearchDocumentRequest document : documents) {
            validateDocument(document.documentId(), document.title(), document.content());
            validateTags(document.tags());
        }
        indexService.requireEnabledIndex(normalizedAppCode, normalizedIndexCode);
        LocalDateTime now = LocalDateTime.now();
        int indexed = 0;
        for (SearchDocumentRequest document : documents) {
            indexed += documentRepository.upsert(buildEntity(normalizedAppCode, normalizedIndexCode,
                    document, now));
        }
        return new BatchIndexResult(documents.size(), indexed);
    }

    private static SearchDocumentEntity buildEntity(String appCode, String indexCode,
                                                    SearchDocumentRequest request,
                                                    LocalDateTime now) {
        SearchDocumentEntity entity = new SearchDocumentEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAppCode(appCode);
        entity.setIndexCode(indexCode);
        entity.setDocumentId(request.documentId().trim());
        entity.setTitle(request.title());
        entity.setSummary(request.summary());
        entity.setContent(request.content());
        entity.setTags(normalizeTags(request.tags()));
        entity.setMetadata(normalizeMetadata(request.metadata()));
        entity.setOwnerId(SearchIndexService.trimToNull(request.ownerId()));
        entity.setOrgId(SearchIndexService.trimToNull(request.orgId()));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    private SearchDocumentEntity requireDocument(String appCode, String indexCode, String documentId) {
        return documentRepository.findByAppCodeAndIndexCodeAndDocumentId(appCode, indexCode, documentId)
                .orElseThrow(() -> new SearchException(SearchErrorCode.DOCUMENT_NOT_FOUND,
                        "Search document not found: " + appCode + "/" + indexCode + "/" + documentId));
    }

    private static void validateDocument(String documentId, String title, String content) {
        if (documentId == null || documentId.isBlank()) {
            throw new SearchException(SearchErrorCode.INVALID_DOCUMENT, "documentId is required");
        }
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasContent = content != null && !content.isBlank();
        if (!hasTitle && !hasContent) {
            throw new SearchException(SearchErrorCode.INVALID_DOCUMENT,
                    "Either title or content must be provided");
        }
    }

    private static void validateTags(List<String> tags) {
        if (tags != null && tags.size() > SearchConstants.MAX_TAGS) {
            throw new SearchException(SearchErrorCode.INVALID_DOCUMENT,
                    "A document may contain at most " + SearchConstants.MAX_TAGS + " tags");
        }
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                distinct.add(tag.trim());
            }
        }
        return new ArrayList<>(distinct);
    }

    private static Map<String, Object> normalizeMetadata(Map<String, Object> metadata) {
        return metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    static SearchDocumentResponse toResponse(SearchDocumentEntity entity) {
        return new SearchDocumentResponse(entity.getId(), entity.getAppCode(), entity.getIndexCode(),
                entity.getDocumentId(), entity.getTitle(), entity.getSummary(), entity.getContent(),
                entity.getTags() == null ? List.of() : List.copyOf(entity.getTags()),
                entity.getMetadata() == null ? Map.of() : new LinkedHashMap<>(entity.getMetadata()),
                entity.getOwnerId(), entity.getOrgId(), entity.getCreateTime(), entity.getUpdateTime());
    }
}