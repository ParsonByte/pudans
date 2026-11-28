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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchQueryServiceTests {

    private final SearchDocumentRepository documentRepository = mock(SearchDocumentRepository.class);
    private final SearchProperties properties = new SearchProperties();
    private final SearchQueryService service = new SearchQueryService(documentRepository,
            properties, new SearchSnippetGenerator(properties));

    @Test
    void appliesDefaultPaginationAndMapping() {
        SearchDocumentEntity document = document("doc-1", "Refund policy",
                "Customers can request a refund within 30 days.");
        when(documentRepository.search(any()))
                .thenReturn(new SearchDocumentPage(List.of(document), 1));

        SearchPageResponse<SearchHitResponse> response = service.search(
                new SearchQuery("orders", null, null, null, null, null, null, null));

        ArgumentCaptor<SearchDocumentCriteria> captor = ArgumentCaptor
                .forClass(SearchDocumentCriteria.class);
        verify(documentRepository).search(captor.capture());
        SearchDocumentCriteria criteria = captor.getValue();
        assertThat(criteria.appCode()).isEqualTo("orders");
        assertThat(criteria.keyword()).isNull();
        assertThat(criteria.sort()).isEqualTo(SearchSort.RELEVANCE);
        assertThat(criteria.offset()).isZero();
        assertThat(criteria.limit()).isEqualTo(20);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().documentId()).isEqualTo("doc-1");
    }

    @Test
    void calculatesOffsetAndPaginationForMiddlePage() {
        when(documentRepository.search(any()))
                .thenReturn(new SearchDocumentPage(List.of(document("doc-11", "Refund", "content")), 25));

        SearchPageResponse<SearchHitResponse> response = service.search(
                new SearchQuery("orders", null, "refund", null, null, 2, 10, null));

        ArgumentCaptor<SearchDocumentCriteria> captor = ArgumentCaptor
                .forClass(SearchDocumentCriteria.class);
        verify(documentRepository).search(captor.capture());
        assertThat(captor.getValue().offset()).isEqualTo(10);
        assertThat(captor.getValue().limit()).isEqualTo(10);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.total()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void reportsLastPageWithoutNextLink() {
        when(documentRepository.search(any()))
                .thenReturn(new SearchDocumentPage(List.of(document("doc-21", "Refund", "content")), 25));

        SearchPageResponse<SearchHitResponse> response = service.search(
                new SearchQuery("orders", null, "refund", null, null, 3, 10, null));

        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void trimsKeywordAndOptionalFilters() {
        when(documentRepository.search(any()))
                .thenReturn(new SearchDocumentPage(List.of(), 0));

        service.search(new SearchQuery(" orders ", " articles ", "  refund  ",
                " user-1 ", " org-1 ", 1, 5, "updated_desc"));

        ArgumentCaptor<SearchDocumentCriteria> captor = ArgumentCaptor
                .forClass(SearchDocumentCriteria.class);
        verify(documentRepository).search(captor.capture());
        SearchDocumentCriteria criteria = captor.getValue();
        assertThat(criteria.appCode()).isEqualTo("orders");
        assertThat(criteria.indexCode()).isEqualTo("articles");
        assertThat(criteria.keyword()).isEqualTo("refund");
        assertThat(criteria.ownerId()).isEqualTo("user-1");
        assertThat(criteria.orgId()).isEqualTo("org-1");
        assertThat(criteria.sort()).isEqualTo(SearchSort.UPDATED_DESC);
        assertThat(criteria.limit()).isEqualTo(5);
    }

    @Test
    void treatsBlankKeywordAsBrowseRequest() {
        when(documentRepository.search(any()))
                .thenReturn(new SearchDocumentPage(List.of(), 0));

        service.search(new SearchQuery("orders", null, "   ", null, null, null, null, null));

        ArgumentCaptor<SearchDocumentCriteria> captor = ArgumentCaptor
                .forClass(SearchDocumentCriteria.class);
        verify(documentRepository).search(captor.capture());
        assertThat(captor.getValue().keyword()).isNull();
        assertThat(captor.getValue().sort()).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    void buildsHighlightedSnippetFromContent() {
        SearchDocumentEntity document = document("doc-1", "Refund policy",
                "Customers can request a refund within 30 days.");
        when(documentRepository.search(any()))
                .thenReturn(new SearchDocumentPage(List.of(document), 1));

        SearchPageResponse<SearchHitResponse> response = service.search(
                new SearchQuery("orders", null, "refund", null, null, null, null, null));

        assertThat(response.items().getFirst().snippet()).contains("<em>refund</em>");
    }

    @Test
    void rejectsMissingAppCode() {
        assertThatThrownBy(() -> service.search(
                new SearchQuery(" ", null, null, null, null, null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_QUERY);
    }

    @Test
    void rejectsOverlongKeyword() {
        properties.setMaxQueryLength(5);

        assertThatThrownBy(() -> service.search(
                new SearchQuery("orders", null, "toolong", null, null, null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.QUERY_TOO_LONG);
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> service.search(
                new SearchQuery("orders", null, null, null, null, 0, 10, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_PAGINATION);
        assertThatThrownBy(() -> service.search(
                new SearchQuery("orders", null, null, null, null, 1, 0, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_PAGINATION);
        assertThatThrownBy(() -> service.search(
                new SearchQuery("orders", null, null, null, null, 1, 1000, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_PAGINATION);
    }

    @Test
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> service.search(
                new SearchQuery("orders", null, null, null, null, null, null, "random")))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_SORT);
    }

    private static SearchDocumentEntity document(String documentId, String title, String content) {
        SearchDocumentEntity entity = new SearchDocumentEntity();
        entity.setId("id-" + documentId);
        entity.setAppCode("orders");
        entity.setIndexCode("articles");
        entity.setDocumentId(documentId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setTags(List.of());
        entity.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        entity.setUpdateTime(LocalDateTime.of(2026, 1, 2, 10, 0));
        return entity;
    }
}