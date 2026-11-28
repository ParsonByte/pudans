package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.entity.SearchDocumentEntity;
import cn.tolim.pudans.search.enums.SearchSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchDocumentRepositoryImplTests {

    @Test
    void searchCountsThenFetchesRequestedPage() {
        EntityManager entityManager = mock(EntityManager.class);
        Query countQuery = mock(Query.class);
        Query selectQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
        when(entityManager.createNativeQuery(anyString(), eq(SearchDocumentEntity.class)))
                .thenReturn(selectQuery);
        when(countQuery.getSingleResult()).thenReturn(25L);
        SearchDocumentEntity document = new SearchDocumentEntity();
        when(selectQuery.getResultList()).thenReturn(List.of(document));
        SearchDocumentRepositoryImpl repository = new SearchDocumentRepositoryImpl(entityManager);
        SearchDocumentCriteria criteria = new SearchDocumentCriteria("orders", "articles", "refund",
                null, null, SearchSort.RELEVANCE, 20, 10);

        SearchDocumentPage page = repository.search(criteria);

        assertThat(page.total()).isEqualTo(25);
        assertThat(page.documents()).containsExactly(document);
        verify(countQuery).setParameter("appCode", "orders");
        verify(countQuery).setParameter("indexCode", "articles");
        verify(countQuery).setParameter("keyword", "refund");
        verify(countQuery).setParameter("likePattern", "%refund%");
        verify(selectQuery).setFirstResult(20);
        verify(selectQuery).setMaxResults(10);
    }

    @Test
    void searchSkipsSelectWhenOffsetIsPastTotal() {
        EntityManager entityManager = mock(EntityManager.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(5L);
        SearchDocumentRepositoryImpl repository = new SearchDocumentRepositoryImpl(entityManager);
        SearchDocumentCriteria criteria = new SearchDocumentCriteria("orders", null, null,
                null, null, SearchSort.UPDATED_DESC, 10, 10);

        SearchDocumentPage page = repository.search(criteria);

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.documents()).isEmpty();
        verify(entityManager, never()).createNativeQuery(anyString(), eq(SearchDocumentEntity.class));
    }

    @Test
    void upsertBindsJsonFieldsAndDocumentValues() {
        EntityManager entityManager = mock(EntityManager.class);
        Query upsertQuery = mock(Query.class);
        when(entityManager.createNativeQuery(contains("insert into pudans_search_document")))
                .thenReturn(upsertQuery);
        when(upsertQuery.executeUpdate()).thenReturn(1);
        SearchDocumentRepositoryImpl repository = new SearchDocumentRepositoryImpl(entityManager);
        SearchDocumentEntity document = document();
        document.setTags(List.of("billing", "refunds"));
        document.setMetadata(Map.of("source", "crm"));

        int updated = repository.upsert(document);

        assertThat(updated).isEqualTo(1);
        verify(upsertQuery).setParameter("id", "document-row-id");
        verify(upsertQuery).setParameter("appCode", "orders");
        verify(upsertQuery).setParameter("indexCode", "articles");
        verify(upsertQuery).setParameter("documentId", "doc-1");
        verify(upsertQuery).setParameter("title", "Refund policy");
        verify(upsertQuery).setParameter("tags", "[\"billing\",\"refunds\"]");
        verify(upsertQuery).setParameter("metadata", "{\"source\":\"crm\"}");
    }

    @Test
    void deleteByIndexBindsIdentity() {
        EntityManager entityManager = mock(EntityManager.class);
        Query deleteQuery = mock(Query.class);
        when(entityManager.createNativeQuery(contains("delete from pudans_search_document")))
                .thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(4);
        SearchDocumentRepositoryImpl repository = new SearchDocumentRepositoryImpl(entityManager);

        int deleted = repository.deleteByIndex("orders", "articles");

        assertThat(deleted).isEqualTo(4);
        verify(deleteQuery).setParameter("appCode", "orders");
        verify(deleteQuery).setParameter("indexCode", "articles");
    }

    @Test
    void deleteDocumentBindsIdentity() {
        EntityManager entityManager = mock(EntityManager.class);
        Query deleteQuery = mock(Query.class);
        when(entityManager.createNativeQuery(contains("delete from pudans_search_document")))
                .thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);
        SearchDocumentRepositoryImpl repository = new SearchDocumentRepositoryImpl(entityManager);

        int deleted = repository.deleteDocument("orders", "articles", "doc-1");

        assertThat(deleted).isEqualTo(1);
        verify(deleteQuery).setParameter("documentId", "doc-1");
    }

    @Test
    void countsDocumentsByIndex() {
        EntityManager entityManager = mock(EntityManager.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(7L);
        SearchDocumentRepositoryImpl repository = new SearchDocumentRepositoryImpl(entityManager);

        long count = repository.countByIndex("orders", "articles");

        assertThat(count).isEqualTo(7);
    }

    @Test
    void groupsDocumentCountsByIndex() {
        EntityManager entityManager = mock(EntityManager.class);
        Query countQuery = mock(Query.class);
        when(entityManager.createNativeQuery(contains("group by index_code"))).thenReturn(countQuery);
        when(countQuery.getResultList()).thenReturn(List.of(new Object[]{"articles", 5L},
                new Object[]{"faq", 2L}));
        SearchDocumentRepositoryImpl repository = new SearchDocumentRepositoryImpl(entityManager);

        List<SearchIndexDocumentCount> counts = repository.countDocumentsByIndex("orders");

        assertThat(counts).containsExactly(new SearchIndexDocumentCount("articles", 5),
                new SearchIndexDocumentCount("faq", 2));
    }

    private static SearchDocumentEntity document() {
        SearchDocumentEntity document = new SearchDocumentEntity();
        document.setId("document-row-id");
        document.setAppCode("orders");
        document.setIndexCode("articles");
        document.setDocumentId("doc-1");
        document.setTitle("Refund policy");
        document.setSummary("Summary");
        document.setContent("Content");
        document.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        document.setUpdateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        return document;
    }
}