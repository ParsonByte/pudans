package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.enums.SearchSort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchDocumentSqlBuilderTests {

    private static SearchDocumentCriteria criteria(String indexCode, String keyword, String ownerId,
                                                   String orgId, SearchSort sort) {
        return new SearchDocumentCriteria("orders", indexCode, keyword, ownerId, orgId, sort, 0, 20);
    }

    @Test
    void buildsBrowseQueryWithoutKeywordPredicates() {
        SearchDocumentSqlBuilder.SqlQuery query = SearchDocumentSqlBuilder
                .buildSelect(criteria(null, null, null, null, SearchSort.RELEVANCE));

        assertThat(query.sql()).startsWith("select d.* from pudans_search_document d");
        assertThat(query.sql()).contains("where d.app_code = :appCode");
        assertThat(query.sql()).doesNotContain("to_tsvector");
        assertThat(query.sql()).doesNotContain("ilike");
        assertThat(query.sql()).contains("order by d.update_time desc, d.id_ desc");
        assertThat(query.parameters()).containsEntry("appCode", "orders")
                .doesNotContainKeys("keyword", "likePattern", "indexCode");
    }

    @Test
    void buildsFullTextQueryWithSubstringFallback() {
        SearchDocumentSqlBuilder.SqlQuery query = SearchDocumentSqlBuilder
                .buildSelect(criteria("articles", "refund policy", null, null, SearchSort.RELEVANCE));

        assertThat(query.sql())
                .contains("@@ websearch_to_tsquery('simple', :keyword)")
                .contains("d.title_ ilike :likePattern escape '\\'")
                .contains("d.summary_ ilike :likePattern escape '\\'")
                .contains("d.content_ ilike :likePattern escape '\\'")
                .contains("d.document_id ilike :likePattern escape '\\'")
                .contains("cast(d.tags_ as text) ilike :likePattern escape '\\'")
                .contains("order by ts_rank_cd(");
        assertThat(query.parameters())
                .containsEntry("keyword", "refund policy")
                .containsEntry("likePattern", "%refund policy%")
                .containsEntry("indexCode", "articles");
    }

    @Test
    void escapesLikeWildcardsInKeyword() {
        SearchDocumentSqlBuilder.SqlQuery query = SearchDocumentSqlBuilder
                .buildSelect(criteria(null, "50%_off\\now", null, null, SearchSort.RELEVANCE));

        assertThat(query.parameters()).containsEntry("likePattern", "%50\\%\\_off\\\\now%");
        assertThat(query.parameters()).containsEntry("keyword", "50%_off\\now");
    }

    @Test
    void appliesScopeFilters() {
        SearchDocumentSqlBuilder.SqlQuery query = SearchDocumentSqlBuilder
                .buildSelect(criteria("articles", "refund", "user-1", "org-1", SearchSort.UPDATED_DESC));

        assertThat(query.sql())
                .contains("d.index_code = :indexCode")
                .contains("d.owner_id = :ownerId")
                .contains("d.org_id = :orgId");
        assertThat(query.parameters())
                .containsEntry("indexCode", "articles")
                .containsEntry("ownerId", "user-1")
                .containsEntry("orgId", "org-1");
    }

    @Test
    void appliesEverySortOption() {
        assertThat(SearchDocumentSqlBuilder.buildSelect(
                criteria(null, null, null, null, SearchSort.CREATED_DESC)).sql())
                .contains("order by d.create_time desc");
        assertThat(SearchDocumentSqlBuilder.buildSelect(
                criteria(null, null, null, null, SearchSort.TITLE_ASC)).sql())
                .contains("order by d.title_ asc nulls last");
        assertThat(SearchDocumentSqlBuilder.buildSelect(
                criteria(null, null, null, null, SearchSort.UPDATED_DESC)).sql())
                .contains("order by d.update_time desc");
        assertThat(SearchDocumentSqlBuilder.buildSelect(
                criteria("articles", "refund", null, null, SearchSort.RELEVANCE)).sql())
                .contains("order by ts_rank_cd(");
    }

    @Test
    void relevanceFallsBackToRecencyForBrowseRequests() {
        SearchDocumentSqlBuilder.SqlQuery query = SearchDocumentSqlBuilder
                .buildSelect(criteria(null, "   ", null, null, SearchSort.RELEVANCE));

        assertThat(query.sql()).contains("order by d.update_time desc, d.id_ desc");
        assertThat(query.sql()).doesNotContain("ts_rank_cd");
        assertThat(query.parameters()).doesNotContainKeys("keyword", "likePattern");
    }

    @Test
    void buildsCountQueryWithoutOrdering() {
        SearchDocumentSqlBuilder.SqlQuery query = SearchDocumentSqlBuilder
                .buildCount(criteria("articles", "refund", null, null, SearchSort.TITLE_ASC));

        assertThat(query.sql()).startsWith("select count(*) from pudans_search_document d");
        assertThat(query.sql()).contains("@@ websearch_to_tsquery('simple', :keyword)");
        assertThat(query.sql()).doesNotContain("order by");
        assertThat(query.parameters()).containsEntry("keyword", "refund")
                .containsEntry("likePattern", "%refund%")
                .containsEntry("indexCode", "articles");
    }
}