package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.entity.SearchDocumentEntity;
import cn.tolim.pudans.search.error.SearchErrorCode;
import cn.tolim.pudans.search.error.SearchException;
import com.alibaba.fastjson2.JSON;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchDocumentRepositoryImpl implements SearchDocumentRepositoryCustom {

    private static final String UPSERT_SQL = """
            insert into pudans_search_document
                (id_, app_code, index_code, document_id, title_, summary_, content_, tags_, metadata_,
                 owner_id, org_id, create_time, update_time)
            values
                (:id, :appCode, :indexCode, :documentId, :title, :summary, :content,
                 cast(:tags as jsonb), cast(:metadata as jsonb), :ownerId, :orgId, :createTime, :updateTime)
            on conflict (app_code, index_code, document_id) do update set
                title_ = excluded.title_,
                summary_ = excluded.summary_,
                content_ = excluded.content_,
                tags_ = excluded.tags_,
                metadata_ = excluded.metadata_,
                owner_id = excluded.owner_id,
                org_id = excluded.org_id,
                update_time = excluded.update_time
            """;

    private static final String DELETE_BY_INDEX_SQL = """
            delete from pudans_search_document
            where app_code = :appCode and index_code = :indexCode
            """;

    private static final String DELETE_DOCUMENT_SQL = """
            delete from pudans_search_document
            where app_code = :appCode and index_code = :indexCode and document_id = :documentId
            """;

    private static final String COUNT_BY_INDEX_SQL = """
            select count(*) from pudans_search_document
            where app_code = :appCode and index_code = :indexCode
            """;

    private static final String COUNT_BY_APP_SQL = """
            select index_code, count(*) from pudans_search_document
            where app_code = :appCode
            group by index_code
            """;

    private final EntityManager entityManager;

    public SearchDocumentRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SearchDocumentPage search(SearchDocumentCriteria criteria) {
        SearchDocumentSqlBuilder.SqlQuery countSql = SearchDocumentSqlBuilder.buildCount(criteria);
        Query countQuery = entityManager.createNativeQuery(countSql.sql());
        bind(countQuery, countSql.parameters());
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0 || criteria.offset() >= total) {
            return new SearchDocumentPage(List.of(), total);
        }
        SearchDocumentSqlBuilder.SqlQuery selectSql = SearchDocumentSqlBuilder.buildSelect(criteria);
        Query selectQuery = entityManager.createNativeQuery(selectSql.sql(), SearchDocumentEntity.class);
        bind(selectQuery, selectSql.parameters());
        selectQuery.setFirstResult(criteria.offset());
        selectQuery.setMaxResults(criteria.limit());
        List<SearchDocumentEntity> documents = selectQuery.getResultList();
        return new SearchDocumentPage(documents, total);
    }

    @Override
    public int upsert(SearchDocumentEntity document) {
        Query query = entityManager.createNativeQuery(UPSERT_SQL);
        query.setParameter("id", document.getId());
        query.setParameter("appCode", document.getAppCode());
        query.setParameter("indexCode", document.getIndexCode());
        query.setParameter("documentId", document.getDocumentId());
        query.setParameter("title", document.getTitle());
        query.setParameter("summary", document.getSummary());
        query.setParameter("content", document.getContent());
        query.setParameter("tags", toJson(document.getTags(), "[]", "tags"));
        query.setParameter("metadata", toJson(document.getMetadata(), "{}", "metadata"));
        query.setParameter("ownerId", document.getOwnerId());
        query.setParameter("orgId", document.getOrgId());
        query.setParameter("createTime", document.getCreateTime());
        query.setParameter("updateTime", document.getUpdateTime());
        return query.executeUpdate();
    }

    @Override
    public int deleteByIndex(String appCode, String indexCode) {
        Query query = entityManager.createNativeQuery(DELETE_BY_INDEX_SQL);
        query.setParameter("appCode", appCode);
        query.setParameter("indexCode", indexCode);
        return query.executeUpdate();
    }

    @Override
    public int deleteDocument(String appCode, String indexCode, String documentId) {
        Query query = entityManager.createNativeQuery(DELETE_DOCUMENT_SQL);
        query.setParameter("appCode", appCode);
        query.setParameter("indexCode", indexCode);
        query.setParameter("documentId", documentId);
        return query.executeUpdate();
    }

    @Override
    public long countByIndex(String appCode, String indexCode) {
        Query query = entityManager.createNativeQuery(COUNT_BY_INDEX_SQL);
        query.setParameter("appCode", appCode);
        query.setParameter("indexCode", indexCode);
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SearchIndexDocumentCount> countDocumentsByIndex(String appCode) {
        Query query = entityManager.createNativeQuery(COUNT_BY_APP_SQL);
        query.setParameter("appCode", appCode);
        List<Object[]> rows = query.getResultList();
        List<SearchIndexDocumentCount> counts = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            counts.add(new SearchIndexDocumentCount((String) row[0], ((Number) row[1]).longValue()));
        }
        return counts;
    }

    private static void bind(Query query, Map<String, Object> parameters) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    private static String toJson(Object value, String fallback, String field) {
        if (value == null) {
            return fallback;
        }
        try {
            return JSON.toJSONString(value);
        } catch (RuntimeException exception) {
            throw new SearchException(SearchErrorCode.INVALID_DOCUMENT,
                    "Document " + field + " cannot be serialized to JSON");
        }
    }
}