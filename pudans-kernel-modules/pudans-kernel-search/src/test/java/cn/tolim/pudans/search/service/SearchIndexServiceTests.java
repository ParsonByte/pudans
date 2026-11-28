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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchIndexServiceTests {

    private final SearchIndexRepository indexRepository = mock(SearchIndexRepository.class);
    private final SearchDocumentRepository documentRepository = mock(SearchDocumentRepository.class);
    private final SearchProperties properties = new SearchProperties();
    private final SearchIndexService service = new SearchIndexService(indexRepository,
            documentRepository, properties);

    @Test
    void createsIndexWithDefaults() {
        when(indexRepository.existsByAppCodeAndIndexCode("orders", "articles")).thenReturn(false);
        when(indexRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SearchIndexResponse response = service.createIndex(
                new SearchIndexCreateRequest("orders", "articles", null, null, null));

        assertThat(response.appCode()).isEqualTo("orders");
        assertThat(response.indexCode()).isEqualTo("articles");
        assertThat(response.name()).isEqualTo("articles");
        assertThat(response.enabled()).isTrue();
        assertThat(response.documentCount()).isZero();
        verify(indexRepository).save(any());
    }

    @Test
    void createsIndexWithExplicitMetadata() {
        when(indexRepository.existsByAppCodeAndIndexCode("orders", "articles")).thenReturn(false);
        when(indexRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SearchIndexResponse response = service.createIndex(new SearchIndexCreateRequest(
                " orders ", " articles ", " Order articles ", " " + "Knowledge base", Boolean.FALSE));

        assertThat(response.appCode()).isEqualTo("orders");
        assertThat(response.name()).isEqualTo("Order articles");
        assertThat(response.description()).isEqualTo("Knowledge base");
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void rejectsDuplicateIndex() {
        when(indexRepository.existsByAppCodeAndIndexCode("orders", "articles")).thenReturn(true);

        assertThatThrownBy(() -> service.createIndex(
                new SearchIndexCreateRequest("orders", "articles", null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INDEX_ALREADY_EXISTS);
        verify(indexRepository, never()).save(any());
    }

    @Test
    void rejectsIndexWithoutCodes() {
        assertThatThrownBy(() -> service.createIndex(
                new SearchIndexCreateRequest(" ", "articles", null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_INDEX);
        verifyNoInteractions(indexRepository);
    }

    @Test
    void getIndexIncludesDocumentCount() {
        when(indexRepository.findByAppCodeAndIndexCode("orders", "articles"))
                .thenReturn(Optional.of(index("articles", true)));
        when(documentRepository.countByIndex("orders", "articles")).thenReturn(3L);

        SearchIndexResponse response = service.getIndex("orders", "articles");

        assertThat(response.documentCount()).isEqualTo(3);
        assertThat(response.indexCode()).isEqualTo("articles");
    }

    @Test
    void getIndexFailsWhenMissing() {
        when(indexRepository.findByAppCodeAndIndexCode("orders", "missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getIndex("orders", "missing"))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INDEX_NOT_FOUND);
    }

    @Test
    void listsIndexesWithGroupedDocumentCounts() {
        when(indexRepository.findAllByAppCodeOrderByIndexCodeAsc("orders"))
                .thenReturn(List.of(index("articles", true), index("faq", true)));
        when(documentRepository.countDocumentsByIndex("orders"))
                .thenReturn(List.of(new SearchIndexDocumentCount("articles", 5)));

        List<SearchIndexResponse> responses = service.listIndexes("orders");

        assertThat(responses).extracting(SearchIndexResponse::indexCode)
                .containsExactly("articles", "faq");
        assertThat(responses).extracting(SearchIndexResponse::documentCount)
                .containsExactly(5L, 0L);
    }

    @Test
    void listIndexesRequiresAppCode() {
        assertThatThrownBy(() -> service.listIndexes(" "))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_QUERY);
    }

    @Test
    void updatesOnlyProvidedFields() {
        SearchIndexEntity entity = index("articles", true);
        entity.setName("Old name");
        entity.setDescription("Old description");
        when(indexRepository.findByAppCodeAndIndexCode("orders", "articles"))
                .thenReturn(Optional.of(entity));
        when(indexRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.countByIndex("orders", "articles")).thenReturn(2L);

        SearchIndexResponse response = service.updateIndex("orders", "articles",
                new SearchIndexUpdateRequest("New name", null, Boolean.FALSE));

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.description()).isEqualTo("Old description");
        assertThat(response.enabled()).isFalse();
        assertThat(response.documentCount()).isEqualTo(2);
    }

    @Test
    void updateIndexFailsWhenMissing() {
        when(indexRepository.findByAppCodeAndIndexCode("orders", "missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateIndex("orders", "missing",
                new SearchIndexUpdateRequest("Name", null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INDEX_NOT_FOUND);
    }

    @Test
    void deletesIndexAndDocuments() {
        SearchIndexEntity entity = index("articles", true);
        when(documentRepository.deleteByIndex("orders", "articles")).thenReturn(4);
        when(indexRepository.findByAppCodeAndIndexCode("orders", "articles"))
                .thenReturn(Optional.of(entity));

        SearchIndexDeleteResult result = service.deleteIndex("orders", "articles");

        assertThat(result.deleted()).isTrue();
        assertThat(result.deletedDocuments()).isEqualTo(4);
        verify(indexRepository).delete(entity);
    }

    @Test
    void deleteIndexIsIdempotentForMissingRegistryEntry() {
        when(documentRepository.deleteByIndex("orders", "articles")).thenReturn(2);
        when(indexRepository.findByAppCodeAndIndexCode("orders", "articles"))
                .thenReturn(Optional.empty());

        SearchIndexDeleteResult result = service.deleteIndex("orders", "articles");

        assertThat(result.deleted()).isFalse();
        assertThat(result.deletedDocuments()).isEqualTo(2);
        verify(indexRepository, never()).delete(any());
    }

    @Test
    void requireEnabledIndexCreatesMissingIndexWhenAllowed() {
        when(indexRepository.findByAppCodeAndIndexCode("orders", "articles"))
                .thenReturn(Optional.empty());
        when(indexRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SearchIndexEntity entity = service.requireEnabledIndex("orders", "articles");

        assertThat(entity.getAppCode()).isEqualTo("orders");
        assertThat(entity.getIndexCode()).isEqualTo("articles");
        assertThat(entity.getEnabled()).isTrue();
        verify(indexRepository).save(any());
    }

    @Test
    void requireEnabledIndexFailsWhenAutoCreateDisabled() {
        properties.setAutoCreateIndex(false);
        when(indexRepository.findByAppCodeAndIndexCode("orders", "articles"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireEnabledIndex("orders", "articles"))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INDEX_NOT_FOUND);
        verify(indexRepository, never()).save(any());
    }

    @Test
    void requireEnabledIndexRejectsDisabledIndex() {
        when(indexRepository.findByAppCodeAndIndexCode("orders", "articles"))
                .thenReturn(Optional.of(index("articles", false)));

        assertThatThrownBy(() -> service.requireEnabledIndex("orders", "articles"))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INDEX_DISABLED);
    }

    private static SearchIndexEntity index(String indexCode, boolean enabled) {
        SearchIndexEntity entity = new SearchIndexEntity();
        entity.setId("id-" + indexCode);
        entity.setAppCode("orders");
        entity.setIndexCode(indexCode);
        entity.setName(indexCode);
        entity.setEnabled(enabled);
        entity.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        entity.setUpdateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        return entity;
    }
}