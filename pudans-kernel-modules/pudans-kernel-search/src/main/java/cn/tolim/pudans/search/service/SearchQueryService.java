package cn.tolim.pudans.search.service;

import cn.tolim.pudans.search.config.SearchProperties;
import cn.tolim.pudans.search.entity.SearchDocumentEntity;
import cn.tolim.pudans.search.enums.SearchSort;
import cn.tolim.pudans.search.error.SearchErrorCode;
import cn.tolim.pudans.search.error.SearchException;
import cn.tolim.pudans.search.model.SearchHitResponse;
import cn.tolim.pudans.search.model.SearchPageResponse;
import cn.tolim.pudans.search.model.SearchQuery;
import cn.tolim.pudans.search.repository.SearchDocumentCriteria;
import cn.tolim.pudans.search.repository.SearchDocumentPage;
import cn.tolim.pudans.search.repository.SearchDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SearchQueryService {

    private final SearchDocumentRepository documentRepository;
    private final SearchProperties properties;
    private final SearchSnippetGenerator snippetGenerator;

    public SearchQueryService(SearchDocumentRepository documentRepository,
                              SearchProperties properties,
                              SearchSnippetGenerator snippetGenerator) {
        this.documentRepository = documentRepository;
        this.properties = properties;
        this.snippetGenerator = snippetGenerator;
    }

    // Run a paginated full-text query scoped to one application
    public SearchPageResponse<SearchHitResponse> search(SearchQuery query) {
        if (query == null) {
            throw new SearchException(SearchErrorCode.INVALID_QUERY, "Search query is required");
        }
        String appCode = SearchIndexService.requireText(query.appCode(),
                SearchErrorCode.INVALID_QUERY, "appCode");
        String keyword = normalizeKeyword(query.keyword());
        int page = resolvePage(query.page());
        int size = resolveSize(query.size());
        SearchSort sort = resolveSort(query.sort());
        long offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) {
            throw new SearchException(SearchErrorCode.INVALID_PAGINATION,
                    "Requested page is out of range");
        }
        SearchDocumentCriteria criteria = new SearchDocumentCriteria(appCode,
                SearchIndexService.trimToNull(query.indexCode()), keyword,
                SearchIndexService.trimToNull(query.ownerId()),
                SearchIndexService.trimToNull(query.orgId()),
                sort, (int) offset, size);
        SearchDocumentPage result = documentRepository.search(criteria);
        List<SearchHitResponse> items = result.documents().stream()
                .map(document -> toHit(document, keyword))
                .toList();
        long total = result.total();
        int totalPages = total == 0 ? 0
                : (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        boolean hasNext = (long) page * size < total;
        return new SearchPageResponse<>(items, page, size, total, totalPages, hasNext);
    }

    private SearchHitResponse toHit(SearchDocumentEntity document, String keyword) {
        String snippet = snippetGenerator.generate(document.getTitle(), document.getSummary(),
                document.getContent(), keyword);
        return new SearchHitResponse(document.getId(), document.getDocumentId(), document.getTitle(),
                document.getSummary(), snippet,
                document.getTags() == null ? List.of() : List.copyOf(document.getTags()),
                document.getOwnerId(), document.getOrgId(),
                document.getCreateTime(), document.getUpdateTime());
    }

    private String normalizeKeyword(String keyword) {
        String normalized = SearchIndexService.trimToNull(keyword);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > properties.getMaxQueryLength()) {
            throw new SearchException(SearchErrorCode.QUERY_TOO_LONG,
                    "Search query may contain at most " + properties.getMaxQueryLength() + " characters");
        }
        return normalized;
    }

    private static int resolvePage(Integer page) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            throw new SearchException(SearchErrorCode.INVALID_PAGINATION, "page must be at least 1");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return properties.getDefaultPageSize();
        }
        if (size < 1 || size > properties.getMaxPageSize()) {
            throw new SearchException(SearchErrorCode.INVALID_PAGINATION,
                    "size must be between 1 and " + properties.getMaxPageSize());
        }
        return size;
    }

    private static SearchSort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SearchSort.RELEVANCE;
        }
        SearchSort resolved = SearchSort.from(sort);
        if (resolved == null) {
            throw new SearchException(SearchErrorCode.INVALID_SORT,
                    "Unsupported sort option: " + sort);
        }
        return resolved;
    }
}