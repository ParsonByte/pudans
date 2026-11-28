package cn.tolim.pudans.iam.communal.repository.app.impl;

import cn.tolim.pudans.iam.communal.entity.app.po.AppGroupPO;
import cn.tolim.pudans.iam.communal.entity.app.query.AppGroupQueryParam;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupRepositoryCustomized;
import cn.tolim.pudans.iam.infra.repository.aspect.query.QuerySingleResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.tolim.pudans.iam.communal.enums.app.AuthorizationType.ALL_ACCESS;


@Repository
@AllArgsConstructor
public class AppGroupRepositoryCustomizedImpl implements AppGroupRepositoryCustomized {

    // EntityManager
    private final EntityManager entityManager;

    // Returns a page of application groups matching the query.
    @SuppressWarnings("DuplicatedCode")
    @Override
    public Page<AppGroupPO> getAppGroupList(AppGroupQueryParam query, Pageable pageable) {
        Map<String, Object> args = new HashMap<>();
        String hql = """
                SELECT
                	NEW cn.tolim.pudans.iam.communal.entity.app.po.AppGroupPO(group.id,
                	group.name,
                	group.code,
                	group.type,
                	group.createTime,
                	group.remark,
                	IFNULL(ass.appCount, 0 ) AS appCount)
                FROM
                	AppGroupEntity group
                	LEFT JOIN (
                	SELECT
                	    aga.groupId as groupId,
                		COUNT(app.id) AS appCount
                	FROM
                		AppGroupAssociationEntity aga
                		INNER JOIN AppEntity app ON aga.app.id = app.id
                	GROUP BY
                		aga.groupId
                	) ass ON group.id = ass.groupId
                """;
        String whereSql = " WHERE 1 = 1 ";
        // Group name
        if (StringUtils.isNoneBlank(query.getName())) {
            whereSql += " AND group.name LIKE :name";
            args.put("name", "%" + query.getName() + "%");
        }
        // Group code
        if (StringUtils.isNoneBlank(query.getCode())) {
            whereSql += " AND group.code LIKE :code";
            args.put("code", "%" + query.getCode() + "%");
        }
        // Group type
        if (ObjectUtils.isNotEmpty(query.getType())) {
            whereSql += " AND group.type = :type";
            args.put("type", query.getType());
        }
        // Sort by creation time descending
        TypedQuery<AppGroupPO> listQuery = entityManager
                .createQuery(hql + whereSql + " ORDER BY group.createTime DESC", AppGroupPO.class);
        args.forEach(listQuery::setParameter);
        listQuery.setFirstResult((int) pageable.getOffset());
        listQuery.setMaxResults(pageable.getPageSize());
        String countSql = """
                SELECT
                 	COUNT(DISTINCT group.id)
                 FROM
                 	AppGroupEntity group
                 	LEFT JOIN (
                 	SELECT
                 		aga.groupId AS groupId,
                 		COUNT(app.id) AS appCount
                 	FROM
                 		AppGroupAssociationEntity aga
                 		INNER JOIN AppEntity app ON aga.app.id = app.id
                 	GROUP BY
                 		aga.groupId
                 	) ass ON group.id = ass.groupId
                """;
        TypedQuery<Long> countQuery = entityManager.createQuery(countSql + whereSql, Long.class);
        args.forEach(countQuery::setParameter);
        return new PageImpl<>(listQuery.getResultList(), pageable, countQuery.getSingleResult());
    }

    // Returns application groups visible to the given subject IDs.
    @Override
    public List<AppGroupPO> getAppGroupList(List<String> subjectIds, AppGroupQueryParam query) {
        Map<String, Object> args = new HashMap<>();
        String hql = """
                SELECT
                	NEW cn.tolim.pudans.iam.communal.entity.app.po.AppGroupPO(group.id,
                	group.name,
                	group.code,
                	group.type,
                	group.createTime,
                	group.remark,
                	IFNULL( ass.appCount, 0 ) AS appCount)
                FROM
                	AppGroupEntity group
                	LEFT JOIN (
                	SELECT
                		aga.groupId AS groupId,
                		COUNT( DISTINCT aga.id ) AS appCount
                	FROM
                		AppGroupAssociationEntity aga
                		LEFT JOIN AppEntity app ON aga.app.id = app.id
                		LEFT JOIN AppAccessPolicyEntity app_acce ON app.id = app_acce.appId AND app_acce.enabled = true
                		WHERE (app_acce.subjectId IN (:subjectIds) OR app.authorizationType = :type) %s
                	GROUP BY
                		aga.groupId
                	) ass ON group.id = ass.groupId
                """.formatted(StringUtils.isNoneBlank(query.getAppName()) ? " AND app.name LIKE :appName " : "");
        String whereSql = " WHERE 1 = 1 ";
        // Group name
        if (StringUtils.isNoneBlank(query.getName())) {
            whereSql += "AND group.name LIKE :name";
            args.put("name", "%" + query.getName() + "%");
        }
        // Group code
        if (StringUtils.isNoneBlank(query.getCode())) {
            whereSql += "AND group.code LIKE :code";
            args.put("code", "%" + query.getCode() + "%");
        }
        // Group type
        if (ObjectUtils.isNotEmpty(query.getType())) {
            whereSql += "AND group.type =:type";
            args.put("type", query);
        }
        // Application name
        if (StringUtils.isNoneBlank(query.getAppName())) {
            args.put("appName", "%" + query.getAppName() + "%");
        }
        TypedQuery<AppGroupPO> listQuery = entityManager.createQuery(hql + whereSql,
                AppGroupPO.class);
        args.put("subjectIds", subjectIds);
        args.put("type", ALL_ACCESS);
        args.forEach(listQuery::setParameter);
        return listQuery.getResultList();
    }

    // Returns the number of applications visible to the subjects in the group.
    @Override
    @QuerySingleResult
    public Long getAppCount(List<String> subjectIds, String groupId) {
        String hql = """
                SELECT
                	COUNT(DISTINCT app.id)
                FROM
                	AppEntity app
                	LEFT JOIN AppAccessPolicyEntity app_acce ON app.id = app_acce.appId
                	LEFT JOIN AppGroupAssociationEntity ass ON app.id = ass.app.id
                WHERE
                	app.enabled = true
                	AND  app_acce.subjectId IN (:subjectIds) OR app.authorizationType = :type
                	AND ass.groupId = :groupId
                """;
        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
        query.setParameter("subjectIds", subjectIds);
        query.setParameter("type", ALL_ACCESS.getCode());
        query.setParameter("groupId", groupId);
        return query.getSingleResult();
    }
}
