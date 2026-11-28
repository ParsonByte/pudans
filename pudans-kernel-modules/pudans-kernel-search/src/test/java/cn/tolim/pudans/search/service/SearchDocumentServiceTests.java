package cn.tolim.pudans.search.service;

import cn.tolim.pudans.search.config.SearchProperties;
import cn.tolim.pudans.search.entity.SearchDocumentEntity;
import cn.tolim.pudans.search.entity.SearchIndexEntity;
import cn.tolim.pudans.search.error.SearchErrorCode;
import cn.tolim.pudans.search.error.SearchException;
import cn.tolim.pudans.search.model.BatchIndexResult;
import cn.tolim.pudans.search.model.SearchDocumentBatchRequest;
import cn.tolim.pudans.search.model.SearchDocumentDeleteResult;
import cn.tolim.pudans.search.model.SearchDocumentRequest;
import cn.tolim.pudans.search.model.SearchDocumentResponse;
import cn.tolim.pudans.search.model.SearchDocumentUpdateRequest;
import cn.tolim.pudans.search.repository.SearchDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchDocumentServiceTests {

    private final SearchDocumentRepository documentRepository = mock(SearchDocumentRepository.class);
    private final SearchIndexService indexService = mock(SearchIndexService.class);
    private final SearchProperties properties = new SearchProperties();
    private final SearchDocumentService service = new SearchDocumentService(documentRepository,
            indexService, properties);

    @Test
    void indexDocumentUpsertsNormalizedDocument() {
        when(indexService.requireEnabledIndex("orders", "articles")).thenReturn(enabledIndex());
        AtomicReference<SearchDocumentEntity> stored = new AtomicReference<>();
        when(documentRepository.upsert(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(documentRepository.findByAppCodeAndIndexCodeAndDocumentId(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        SearchDocumentResponse response = service.indexDocument(" orders ", " articles ",
                new SearchDocumentRequest(" doc-1 ", " Refund policy ", null,
                        "Customers can request a refund.", List.of(" billing ", "billing", "", "refunds"),
                        Map.of("source", "crm"), " user-1 ", " "));

        ArgumentCaptor<SearchDocumentEntity> captor = ArgumentCaptor
                .forClass(SearchDocumentEntity.class);
        verify(documentRepository).upsert(captor.capture());
        SearchDocumentEntity entity = captor.getValue();
        assertThat(entity.getId()).isNotBlank();
        assertThat(entity.getAppCode()).isEqualTo("orders");
        assertThat(entity.getIndexCode()).isEqualTo("articles");
        assertThat(entity.getDocumentId()).isEqualTo("doc-1");
        assertThat(entity.getTags()).containsExactly("billing", "refunds");
        assertThat(entity.getMetadata()).containsEntry("source", "crm");
        assertThat(entity.getOwnerId()).isEqualTo("user-1");
        assertThat(entity.getOrgId()).isNull();
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();

        assertThat(response.documentId()).isEqualTo("doc-1");
        assertThat(response.title()).isEqualTo(" Refund policy ");
        assertThat(response.tags()).containsExactly("billing", "refunds");
    }

    @Test
    void indexDocumentRejectsDocumentWithoutTitleOrContent() {
        assertThatThrownBy(() -> service.indexDocument("orders", "articles",
                new SearchDocumentRequest("doc-1", "  ", null, null, null, null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_DOCUMENT);
        verifyNoInteractions(indexService, documentRepository);
    }

    @Test
    void indexDocumentRejectsTooManyTags() {
        List<String> tags = java.util.stream.IntStream.range(0, 51).mapToObj(index -> "tag" + index)
                .toList();

        assertThatThrownBy(() -> service.indexDocument("orders", "articles",
                new SearchDocumentRequest("doc-1", "Title", null, null, tags, null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_DOCUMENT);
        verifyNoInteractions(indexService, documentRepository);
    }

    @Test
    void indexDocumentPropagatesDisabledIndex() {
        when(indexService.requireEnabledIndex("orders", "articles"))
                .thenThrow(new SearchException(SearchErrorCode.INDEX_DISABLED));

        assertThatThrownBy(() -> service.indexDocument("orders", "articles",
                new SearchDocumentRequest("doc-1", "Title", null, "Content", null, null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INDEX_DISABLED);
        verify(documentRepository, never()).upsert(any());
    }

    @Test
    void updateDocumentMutatesExistingEntity() {
        SearchDocumentEntity entity = document("doc-1", "Old title", "Old content");
        when(indexService.requireEnabledIndex("orders", "articles")).thenReturn(enabledIndex());
        when(documentRepository.findByAppCodeAndIndexCodeAndDocumentId("orders", "articles", "doc-1"))
                .thenReturn(Optional.of(entity));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SearchDocumentResponse response = service.updateDocument("orders", "articles", "doc-1",
                new SearchDocumentUpdateRequest("New title", "New summary", "New content",
                        List.of("updated"), Map.of("version", 2), "user-2", "org-2"));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.content()).isEqualTo("New content");
        assertThat(response.tags()).containsExactly("updated");
        assertThat(response.metadata()).containsEntry("version", 2);
        assertThat(response.ownerId()).isEqualTo("user-2");
        assertThat(response.orgId()).isEqualTo("org-2");
        verify(documentRepository).save(entity);
    }

    @Test
    void updateDocumentFailsWhenMissing() {
        when(indexService.requireEnabledIndex("orders", "articles")).thenReturn(enabledIndex());
        when(documentRepository.findByAppCodeAndIndexCodeAndDocumentId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDocument("orders", "articles", "missing",
                new SearchDocumentUpdateRequest("Title", null, "Content", null, null, null, null)))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.DOCUMENT_NOT_FOUND);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void getDocumentFailsWhenMissing() {
        when(documentRepository.findByAppCodeAndIndexCodeAndDocumentId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocument("orders", "articles", "missing"))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    void deleteDocumentReportsWhetherRowWasRemoved() {
        when(documentRepository.deleteDocument("orders", "articles", "doc-1")).thenReturn(1);

        SearchDocumentDeleteResult deleted = service.deleteDocument("orders", "articles", "doc-1");

        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.documentId()).isEqualTo("doc-1");
    }

    @Test
    void deleteDocumentIsIdempotentForMissingDocument() {
        when(documentRepository.deleteDocument("orders", "articles", "missing")).thenReturn(0);

        SearchDocumentDeleteResult deleted = service.deleteDocument("orders", "articles", "missing");

        assertThat(deleted.deleted()).isFalse();
    }

    @Test
    void batchIndexValidatesThenUpsertsEveryDocument() {
        when(indexService.requireEnabledIndex("orders", "articles")).thenReturn(enabledIndex());
        when(documentRepository.upsert(any())).thenReturn(1);
        SearchDocumentBatchRequest request = new SearchDocumentBatchRequest(List.of(
                new SearchDocumentRequest("doc-1", "First", null, "First content", null, null, null, null),
                new SearchDocumentRequest("doc-2", null, null, "Second content", null, null, null, null)));

        BatchIndexResult result = service.indexDocuments("orders", "articles", request);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.indexed()).isEqualTo(2);
        ArgumentCaptor<SearchDocumentEntity> captor = ArgumentCaptor
                .forClass(SearchDocumentEntity.class);
        verify(documentRepository, times(2)).upsert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SearchDocumentEntity::getDocumentId)
                .containsExactly("doc-1", "doc-2");
    }

    @Test
    void batchIndexRejectsOversizedBatchBeforeTouchingStorage() {
        properties.setMaxBatchSize(1);
        SearchDocumentBatchRequest request = new SearchDocumentBatchRequest(List.of(
                new SearchDocumentRequest("doc-1", "First", null, "First content", null, null, null, null),
                new SearchDocumentRequest("doc-2", "Second", null, "Second content", null, null, null, null)));

        assertThatThrownBy(() -> service.indexDocuments("orders", "articles", request))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.BATCH_TOO_LARGE);
        verifyNoInteractions(indexService, documentRepository);
    }

    @Test
    void batchIndexRejectsInvalidDocumentBeforeCreatingIndex() {
        SearchDocumentBatchRequest request = new SearchDocumentBatchRequest(List.of(
                new SearchDocumentRequest("doc-1", "First", null, "First content", null, null, null, null),
                new SearchDocumentRequest("doc-2", null, null, null, null, null, null, null)));

        assertThatThrownBy(() -> service.indexDocuments("orders", "articles", request))
                .isInstanceOf(SearchException.class)
                .extracting(exception -> ((SearchException) exception).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_DOCUMENT);
        verifyNoInteractions(indexService, documentRepository);
    }

    private static SearchIndexEntity enabledIndex() {
        SearchIndexEntity index = new SearchIndexEntity();
        index.setId("index-1");
        index.setAppCode("orders");
        index.setIndexCode("articles");
        index.setName("Articles");
        index.setEnabled(Boolean.TRUE);
        return index;
    }

    private static SearchDocumentEntity document(String documentId, String title, String content) {
        SearchDocumentEntity entity = new SearchDocumentEntity();
        entity.setId("id-" + documentId);
        entity.setAppCode("orders");
        entity.setIndexCode("articles");
        entity.setDocumentId(documentId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        entity.setUpdateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        return entity;
    }
}
