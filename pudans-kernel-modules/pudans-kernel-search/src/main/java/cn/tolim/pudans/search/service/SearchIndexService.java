package cn.tolim.pudans.search.service;

import cn.tolim.pudans.search.config.SearchProperties;
import cn.tolim.pudans.search.entity.SearchIndexEntity;
import cn.tolim.pudans.search.error.SearchErrorCode;
import cn.tolim.pudans.search.error.SearchException;
import cn.tolim.pudans.search.model.SearchIndexCreateRequest;
import cn.tolim.pudans.search.model.SearchIndexDeleteResult;
import cn.tolim.pudans.search.model.SearchIndexResponse;
import cn.tolim.pudans.search.model.SearchIndexUpdateRequest;
import cn.tolim.pudans.search.repository.SearchDocumentRepository;
import cn.tolim.pudans.search.repository.SearchIndexDocumentCount;
import cn.tolim.pudans.search.repository.SearchIndexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SearchIndexService {

    private final SearchIndexRepository indexRepository;
    private final SearchDocumentRepository documentRepository;
    private final SearchProperties properties;

    public SearchIndexService(SearchIndexRepository indexRepository,
                              SearchDocumentRepository documentRepository,
                              SearchProperties properties) {
        this.indexRepository = indexRepository;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    // Create an explicit index registry entry
    @Transactional
    public SearchIndexResponse createIndex(SearchIndexCreateRequest request) {
        String appCode = requireText(request.appCode(), SearchErrorCode.INVALID_INDEX, "appCode");
        String indexCode = requireText(request.indexCode(), SearchErrorCode.INVALID_INDEX, "indexCode");
        if (indexRepository.existsByAppCodeAndIndexCode(appCode, indexCode)) {
            throw new SearchException(SearchErrorCode.INDEX_ALREADY_EXISTS,
                    "Search index already exists: " + appCode + "/" + indexCode);
        }
        SearchIndexEntity entity = new SearchIndexEntity();
        entity.setAppCode(appCode);
        entity.setIndexCode(indexCode);
        entity.setName(trimToNull(request.name()) == null ? indexCode : request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        return toResponse(indexRepository.save(entity), 0);
    }

    // Load one index with its current document count
    @Transactional(readOnly = true)
    public SearchIndexResponse getIndex(String appCode, String indexCode) {
        String normalizedAppCode = requireText(appCode, SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = requireText(indexCode, SearchErrorCode.INVALID_INDEX, "indexCode");
        SearchIndexEntity entity = requireIndex(normalizedAppCode, normalizedIndexCode);
        return toResponse(entity, documentRepository.countByIndex(normalizedAppCode, normalizedIndexCode));
    }

    // List every index of one application with document counts
    @Transactional(readOnly = true)
    public List<SearchIndexResponse> listIndexes(String appCode) {
        String normalizedAppCode = requireText(appCode, SearchErrorCode.INVALID_QUERY, "appCode");
        Map<String, Long> counts = new HashMap<>();
        for (SearchIndexDocumentCount count : documentRepository.countDocumentsByIndex(normalizedAppCode)) {
            counts.put(count.indexCode(), count.documentCount());
        }
        return indexRepository.findAllByAppCodeOrderByIndexCodeAsc(normalizedAppCode).stream()
                .map(entity -> toResponse(entity,
                        counts.getOrDefault(entity.getIndexCode(), 0L)))
                .toList();
    }

    // Update mutable index metadata; null fields are left unchanged
    @Transactional
    public SearchIndexResponse updateIndex(String appCode, String indexCode,
                                           SearchIndexUpdateRequest request) {
        String normalizedAppCode = requireText(appCode, SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = requireText(indexCode, SearchErrorCode.INVALID_INDEX, "indexCode");
        SearchIndexEntity entity = requireIndex(normalizedAppCode, normalizedIndexCode);
        if (request.name() != null) {
            String name = trimToNull(request.name());
            entity.setName(name == null ? normalizedIndexCode : name);
        }
        if (request.description() != null) {
            entity.setDescription(trimToNull(request.description()));
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        SearchIndexEntity saved = indexRepository.save(entity);
        return toResponse(saved, documentRepository.countByIndex(normalizedAppCode, normalizedIndexCode));
    }

    // Delete an index and every document stored in it
    @Transactional
    public SearchIndexDeleteResult deleteIndex(String appCode, String indexCode) {
        String normalizedAppCode = requireText(appCode, SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = requireText(indexCode, SearchErrorCode.INVALID_INDEX, "indexCode");
        long deletedDocuments = documentRepository.deleteByIndex(normalizedAppCode, normalizedIndexCode);
        Optional<SearchIndexEntity> entity = indexRepository
                .findByAppCodeAndIndexCode(normalizedAppCode, normalizedIndexCode);
        entity.ifPresent(indexRepository::delete);
        return new SearchIndexDeleteResult(normalizedAppCode, normalizedIndexCode,
                entity.isPresent(), deletedDocuments);
    }

    // Resolve a writable index, creating the registry entry when auto-create is enabled
    @Transactional
    public SearchIndexEntity requireEnabledIndex(String appCode, String indexCode) {
        String normalizedAppCode = requireText(appCode, SearchErrorCode.INVALID_INDEX, "appCode");
        String normalizedIndexCode = requireText(indexCode, SearchErrorCode.INVALID_INDEX, "indexCode");
        Optional<SearchIndexEntity> existing = indexRepository
                .findByAppCodeAndIndexCode(normalizedAppCode, normalizedIndexCode);
        SearchIndexEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
        } else {
            if (!properties.isAutoCreateIndex()) {
                throw new SearchException(SearchErrorCode.INDEX_NOT_FOUND,
                        "Search index not found: " + normalizedAppCode + "/" + normalizedIndexCode);
            }
            entity = new SearchIndexEntity();
            entity.setAppCode(normalizedAppCode);
            entity.setIndexCode(normalizedIndexCode);
            entity.setName(normalizedIndexCode);
            entity.setEnabled(Boolean.TRUE);
            entity = indexRepository.save(entity);
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new SearchException(SearchErrorCode.INDEX_DISABLED,
                    "Search index is disabled: " + normalizedAppCode + "/" + normalizedIndexCode);
        }
        return entity;
    }

    private SearchIndexEntity requireIndex(String appCode, String indexCode) {
        return indexRepository.findByAppCodeAndIndexCode(appCode, indexCode)
                .orElseThrow(() -> new SearchException(SearchErrorCode.INDEX_NOT_FOUND,
                        "Search index not found: " + appCode + "/" + indexCode));
    }

    private static SearchIndexResponse toResponse(SearchIndexEntity entity, long documentCount) {
        return new SearchIndexResponse(entity.getId(), entity.getAppCode(), entity.getIndexCode(),
                entity.getName(), entity.getDescription(), Boolean.TRUE.equals(entity.getEnabled()),
                documentCount, entity.getCreateTime(), entity.getUpdateTime());
    }

    static String requireText(String value, SearchErrorCode errorCode, String field) {
        if (value == null || value.isBlank()) {
            throw new SearchException(errorCode, field + " is required");
        }
        return value.trim();
    }

    static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}