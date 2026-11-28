package cn.tolim.pudans.search;

import cn.tolim.pudans.search.model.BatchIndexResult;
import cn.tolim.pudans.search.model.SearchDocumentBatchRequest;
import cn.tolim.pudans.search.model.SearchDocumentRequest;
import cn.tolim.pudans.search.model.SearchDocumentResponse;
import cn.tolim.pudans.search.model.SearchHitResponse;
import cn.tolim.pudans.search.model.SearchIndexCreateRequest;
import cn.tolim.pudans.search.model.SearchIndexDeleteResult;
import cn.tolim.pudans.search.model.SearchPageResponse;
import cn.tolim.pudans.search.model.SearchQuery;
import cn.tolim.pudans.search.repository.SearchDocumentRepository;
import cn.tolim.pudans.search.repository.SearchIndexRepository;
import cn.tolim.pudans.search.service.SearchDocumentService;
import cn.tolim.pudans.search.service.SearchIndexService;
import cn.tolim.pudans.search.service.SearchQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SearchPostgresIntegrationTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SearchPostgresIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("pudans_search")
            .withUsername("pudans")
            .withPassword("pudans");

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private SearchDocumentService documentService;

    @Autowired
    private SearchQueryService queryService;

    @Autowired
    private SearchDocumentRepository documentRepository;

    @Autowired
    private SearchIndexRepository indexRepository;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void cleanDatabase() {
        documentRepository.deleteAll();
        indexRepository.deleteAll();
    }

    @Test
    void searchesDocumentsWithFullTextAndBuildsSnippet() {
        indexService.createIndex(new SearchIndexCreateRequest("orders", "articles", "Articles", null, true));
        indexWithMetadata("orders", "articles", "doc-1", "Refund policy", "Thirty day returns",
                "Customers can request a refund within 30 days.", Map.of("source", "crm"));
        indexWithOwner("orders", "articles", "doc-2", "Payment gateway", null,
                "Card payments are processed by the gateway.", null);

        SearchPageResponse<SearchHitResponse> result = queryService.search(
                new SearchQuery("orders", "articles", "refund", null, null, 1, 10, "RELEVANCE"));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().documentId()).isEqualTo("doc-1");
        assertThat(result.items().getFirst().snippet()).contains("<em>refund</em>");
    }

    @Test
    void supportsChineseSubstringSearch() {
        indexService.createIndex(new SearchIndexCreateRequest("orders", "articles", "Articles", null, true));
        indexWithOwner("orders", "articles", "doc-cn", "退款说明", null,
                "订单退款流程说明：客户可以在三十天内申请退款。", null);

        SearchPageResponse<SearchHitResponse> result = queryService.search(
                new SearchQuery("orders", "articles", "退款", null, null, 1, 10, null));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().getFirst().documentId()).isEqualTo("doc-cn");
        assertThat(result.items().getFirst().snippet()).contains("<em>退款</em>");
    }

    @Test
    void paginatesQueryAndBrowseResults() {
        List<SearchDocumentRequest> documents = IntStream.range(0, 25)
                .mapToObj(index -> new SearchDocumentRequest("doc-" + index,
                        "Refund report " + index, null, "Refund case " + index, null, null, null, null))
                .toList();
        BatchIndexResult batch = documentService.indexDocuments("orders", "reports",
                new SearchDocumentBatchRequest(documents));

        assertThat(batch.indexed()).isEqualTo(25);

        SearchPageResponse<SearchHitResponse> firstPage = queryService.search(
                new SearchQuery("orders", "reports", "refund", null, null, 1, 10, null));
        assertThat(firstPage.total()).isEqualTo(25);
        assertThat(firstPage.items()).hasSize(10);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();

        SearchPageResponse<SearchHitResponse> lastPage = queryService.search(
                new SearchQuery("orders", "reports", "refund", null, null, 3, 10, null));
        assertThat(lastPage.items()).hasSize(5);
        assertThat(lastPage.hasNext()).isFalse();

        SearchPageResponse<SearchHitResponse> browsePage = queryService.search(
                new SearchQuery("orders", "reports", null, null, null, 2, 10, "UPDATED_DESC"));
        assertThat(browsePage.total()).isEqualTo(25);
        assertThat(browsePage.items()).hasSize(10);
    }

    @Test
    void filtersByOwner() {
        indexService.createIndex(new SearchIndexCreateRequest("orders", "articles", "Articles", null, true));
        indexWithOwner("orders", "articles", "doc-1", "Refund policy", null, "Refund for user one", "user-1");
        indexWithOwner("orders", "articles", "doc-2", "Refund policy", null, "Refund for user two", "user-2");

        SearchPageResponse<SearchHitResponse> result = queryService.search(
                new SearchQuery("orders", "articles", "refund", "user-1", null, 1, 10, null));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().getFirst().documentId()).isEqualTo("doc-1");
    }

    @Test
    void upsertReplacesExistingDocument() {
        indexService.createIndex(new SearchIndexCreateRequest("orders", "articles", "Articles", null, true));
        indexWithOwner("orders", "articles", "doc-1", "Refund policy", null, "Old refund content", null);
        indexWithOwner("orders", "articles", "doc-1", "Refund policy updated", null, "New refund content", null);

        SearchDocumentResponse document = documentService.getDocument("orders", "articles", "doc-1");
        assertThat(document.title()).isEqualTo("Refund policy updated");

        SearchPageResponse<SearchHitResponse> result = queryService.search(
                new SearchQuery("orders", "articles", "refund", null, null, 1, 10, null));
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void deletesIndexWithDocuments() {
        indexService.createIndex(new SearchIndexCreateRequest("orders", "temporary", "Temporary", null, true));
        indexWithOwner("orders", "temporary", "doc-1", "Temporary one", null, "Temporary content", null);
        indexWithOwner("orders", "temporary", "doc-2", "Temporary two", null, "Temporary content", null);

        SearchIndexDeleteResult deleteResult = indexService.deleteIndex("orders", "temporary");

        assertThat(deleteResult.deleted()).isTrue();
        assertThat(deleteResult.deletedDocuments()).isEqualTo(2);
        assertThat(queryService.search(new SearchQuery("orders", "temporary", null, null, null,
                1, 10, null)).total()).isZero();
    }

    private void indexWithMetadata(String appCode, String indexCode, String documentId, String title,
                                   String summary, String content, Map<String, Object> metadata) {
        documentService.indexDocument(appCode, indexCode, new SearchDocumentRequest(documentId,
                title, summary, content, List.of("support"), metadata, null, null));
    }

    private void indexWithOwner(String appCode, String indexCode, String documentId, String title,
                                String summary, String content, String ownerId) {
        documentService.indexDocument(appCode, indexCode, new SearchDocumentRequest(documentId,
                title, summary, content, List.of("support"), null, ownerId, null));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}