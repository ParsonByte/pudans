package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.constant.SearchConstants;
import cn.tolim.pudans.search.enums.SearchSort;

import java.util.LinkedHashMap;
import java.util.Map;

// Builds parameterized PostgreSQL statements; identifiers are constants, never user input
final class SearchDocumentSqlBuilder {

    private static final String CONFIGURATION = "'" + SearchConstants.TEXT_SEARCH_CONFIGURATION + "'";

    // Concatenated searchable fields converted to a PostgreSQL tsvector at query time
    private static final String SEARCH_VECTOR = "to_tsvector(" + CONFIGURATION
            + ", coalesce(d.title_, '') || ' ' || coalesce(d.summary_, '') || ' '"
            + " || coalesce(d.content_, '') || ' ' || coalesce(cast(d.tags_ as text), ''))";

    private static final String RANK = "ts_rank_cd(" + SEARCH_VECTOR
            + ", websearch_to_tsquery(" + CONFIGURATION + ", :keyword))";

    private SearchDocumentSqlBuilder() {
    }

    static SqlQuery buildSelect(SearchDocumentCriteria criteria) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("select d.* from pudans_search_document d");
        sql.append(buildWhere(criteria, parameters));
        sql.append(buildOrderBy(criteria));
        return new SqlQuery(sql.toString(), parameters);
    }

    static SqlQuery buildCount(SearchDocumentCriteria criteria) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("select count(*) from pudans_search_document d");
        sql.append(buildWhere(criteria, parameters));
        return new SqlQuery(sql.toString(), parameters);
    }

    private static String buildWhere(SearchDocumentCriteria criteria, Map<String, Object> parameters) {
        StringBuilder where = new StringBuilder(" where d.app_code = :appCode");
        parameters.put("appCode", criteria.appCode());
        if (hasText(criteria.indexCode())) {
            where.append(" and d.index_code = :indexCode");
            parameters.put("indexCode", criteria.indexCode());
        }
        if (hasText(criteria.ownerId())) {
            where.append(" and d.owner_id = :ownerId");
            parameters.put("ownerId", criteria.ownerId());
        }
        if (hasText(criteria.orgId())) {
            where.append(" and d.org_id = :orgId");
            parameters.put("orgId", criteria.orgId());
        }
        if (hasText(criteria.keyword())) {
            parameters.put("keyword", criteria.keyword());
            parameters.put("likePattern", "%" + escapeLikePattern(criteria.keyword()) + "%");
            where.append(" and (").append(SEARCH_VECTOR)
                    .append(" @@ websearch_to_tsquery(").append(CONFIGURATION).append(", :keyword)")
                    .append(" or d.title_ ilike :likePattern escape '\\'")
                    .append(" or d.summary_ ilike :likePattern escape '\\'")
                    .append(" or d.content_ ilike :likePattern escape '\\'")
                    .append(" or d.document_id ilike :likePattern escape '\\'")
                    .append(" or cast(d.tags_ as text) ilike :likePattern escape '\\'")
                    .append(")");
        }
        return where.toString();
    }

    private static String buildOrderBy(SearchDocumentCriteria criteria) {
        SearchSort sort = criteria.sort() == null ? SearchSort.RELEVANCE : criteria.sort();
        return switch (sort) {
            case RELEVANCE -> hasText(criteria.keyword())
                    ? " order by " + RANK + " desc, d.update_time desc, d.id_ desc"
                    : " order by d.update_time desc, d.id_ desc";
            case UPDATED_DESC -> " order by d.update_time desc, d.id_ desc";
            case CREATED_DESC -> " order by d.create_time desc, d.id_ desc";
            case TITLE_ASC -> " order by d.title_ asc nulls last, d.id_ asc";
        };
    }

    // Escape LIKE wildcards so user input is always treated as literal text
    static String escapeLikePattern(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '%' || character == '_') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record SqlQuery(String sql, Map<String, Object> parameters) {
    }
}