package cn.tolim.pudans.iam.communal.repository.app.impl;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppPO;
import cn.tolim.pudans.iam.communal.entity.app.query.GetAppListQueryParam;
import cn.tolim.pudans.iam.communal.repository.app.AppRepositoryCustomized;
import cn.tolim.pudans.iam.infra.repository.aspect.query.QuerySingleResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.enums.app.AuthorizationType.ALL_ACCESS;


@Repository
@AllArgsConstructor
public class AppRepositoryCustomizedImpl implements AppRepositoryCustomized {

    // EntityManager
    private final EntityManager entityManager;

    // Returns applications visible to the given subject IDs.
    @Override
    public List<AppPO> getAppList(List<String> subjectIds) {
        Map<String, Object> args = new HashMap<>();
        String hql = """
                SELECT
                	NEW cn.tolim.pudans.iam.communal.entity.app.po.AppPO(app.id,app.name,app.code,app.clientId,app.clientSecret,
                    app.template,app.protocol,app.type,app.icon,app.initLoginUrl,app.authorizationType,app.enabled,LISTAGG(DISTINCT ass.groupId,","))
                FROM
                	AppEntity app
                	LEFT JOIN AppAccessPolicyEntity appAcce ON app.id = appAcce.appId AND appAcce.enabled = true
                	LEFT JOIN AppGroupAssociationEntity ass ON app.id = ass.app.id
                WHERE
                	app.enabled = true
                	AND (appAcce.subjectId IN (:subjectIds) OR app.authorizationType = :type)
                GROUP BY app.id
                """;
        TypedQuery<AppPO> listQuery = entityManager.createQuery(hql, AppPO.class);
        args.put("subjectIds", subjectIds);
        args.put("type", ALL_ACCESS);
        args.forEach(listQuery::setParameter);
        return listQuery.getResultList();
    }

    // Returns a filtered page of applications visible to the given subject IDs.
    @Override
    public Page<AppEntity> getAppList(List<String> subjectIds, GetAppListQueryParam query,
                                      Pageable pageable) {
        Map<String, Object> args = new HashMap<>();
        String hql = """
                SELECT DISTINCT
                	app
                FROM
                	AppEntity app
                	LEFT JOIN AppAccessPolicyEntity appAcce ON app.id = appAcce.appId
                	LEFT JOIN AppGroupAssociationEntity ass ON app.id = ass.app.id
                WHERE
                	app.enabled = true
                	AND (appAcce.subjectId IN (:subjectIds) AND appAcce.enabled = true OR app.authorizationType = :type)
                """;
        String whereSql = "";
        // Application name
        if (org.apache.commons.lang3.StringUtils.isNoneBlank(query.getName())) {
            whereSql += " AND app.name LIKE :name ";
            args.put("name", "%" + query.getName() + "%");
        }

        // Group ID
        if (Objects.nonNull(query.getGroupId())) {
            whereSql += " AND ass.groupId = :groupId";
            args.put("groupId", query.getGroupId());
        }

        TypedQuery<AppEntity> listQuery = entityManager.createQuery(hql + whereSql,
                AppEntity.class);
        args.put("subjectIds", subjectIds);
        args.put("type", ALL_ACCESS);
        args.forEach(listQuery::setParameter);
        listQuery.setFirstResult((int) pageable.getOffset());
        listQuery.setMaxResults(pageable.getPageSize());
        String countSql = """
                SELECT
                	COUNT(DISTINCT app.id)
                FROM
                	AppEntity app
                	LEFT JOIN AppAccessPolicyEntity appAcce ON app.id = appAcce.appId
                	LEFT JOIN AppGroupAssociationEntity ass ON app.id = ass.app.id
                WHERE
                	app.enabled = true
                	AND (appAcce.subjectId IN (:subjectIds) AND appAcce.enabled = true OR app.authorizationType = :type)
                """;
        TypedQuery<Long> countQuery = entityManager.createQuery(countSql + whereSql, Long.class);
        args.forEach(countQuery::setParameter);
        return new PageImpl<>(listQuery.getResultList(), pageable, countQuery.getSingleResult());
    }

    // Returns the number of applications visible to the given subject IDs.
    @Override
    @QuerySingleResult
    public Long getAppCount(List<String> subjectIds) {
        String hql = """
                SELECT
                    COUNT(DISTINCT app.id)
                FROM
                    AppEntity app
                LEFT JOIN
                    AppAccessPolicyEntity aap ON app.id = aap.appId
                WHERE
                    app.enabled = true
                    AND (aap.subjectId IN (:subjectIds) AND aap.enabled = true OR app.authorizationType =:authorizationType)
                """;
        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
        query.setParameter("subjectIds", subjectIds);
        query.setParameter("authorizationType", ALL_ACCESS);
        return query.getSingleResult();
    }
}
