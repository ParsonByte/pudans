package cn.tolim.pudans.iam.communal.repository.app.impl;

import cn.tolim.pudans.iam.communal.entity.app.po.AppAccessPolicyPO;
import cn.tolim.pudans.iam.communal.entity.app.query.AppAccessPolicyQueryParam;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepositoryCustomized;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;


@Repository
public class AppAccessPolicyRepositoryCustomizedImpl implements
        AppAccessPolicyRepositoryCustomized {

    // EntityManager
    private final EntityManager entityManager;

    public AppAccessPolicyRepositoryCustomizedImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Get application access policy list
    // Parameter pageable: Pageable
    // Parameter query: AppAccessPolicyQueryParam
    // Returns: Page
    @Override
    public Page<AppAccessPolicyPO> getAppPolicyList(AppAccessPolicyQueryParam query,
                                                    Pageable pageable) {
        Map<String, Object> args = new HashMap<>();
        String hql = """
                SELECT
                	NEW cn.tolim.pudans.iam.communal.entity.app.po.AppAccessPolicyPO(a.id,
                	a.appId,
                	a.subjectId,
                	a.subjectType,
                	a.enabled,
                	a.createTime,
                	subject.name,
                	app.name,
                	app.icon,
                	app.type,
                	app.template,
                	app.protocol)
                FROM
                	AppAccessPolicyEntity a
                	INNER JOIN AppEntity app ON a.appId = app.id
                INNER JOIN
                (
                SELECT
                    id as id,
                	name as name
                FROM
                	UserGroupEntity UNION ALL
                SELECT
                	id as id,
                	name as name
                FROM
                	OrganizationEntity UNION ALL
                SELECT
                	id as id,
                	username AS name
                FROM
                	UserEntity
                	) subject ON a.subjectId = subject.id
                WHERE 1=1
                """;
        String whereSql = "";
        if (ObjectUtils.isNotEmpty(query.getSubjectType())) {
            whereSql += " AND subjectType = :subjectType";
            args.put("subjectType", query.getSubjectType());
        }
        // Principal name
        if (StringUtils.isNotEmpty(query.getSubjectName())) {
            whereSql += " AND subject.name LIKE :subjectName";
            args.put("subjectName", "%" + query.getSubjectName() + "%");
        }

        // Principal ID
        if (StringUtils.isNotBlank(query.getSubjectId())) {
            whereSql += " AND a.subjectId = :subjectId";
            args.put("subjectId", query.getSubjectId());
        } else {
            if (StringUtils.isEmpty(query.getAppId())) {
                throw new PudansException("主体ID不能为空");
            }
        }

        // Application ID
        if (StringUtils.isNotBlank(query.getAppId())) {
            whereSql += " AND a.appId = :appId";
            args.put("appId", query.getAppId());
        }

        // Application name
        if (StringUtils.isNotBlank(query.getAppName())) {
            whereSql += " AND app.name LIKE :appName";
            args.put("appName", "%" + query.getAppName() + "%");
        }

        // Sort by creation time descending
        TypedQuery<AppAccessPolicyPO> listQuery = entityManager.createQuery(hql + whereSql,
                AppAccessPolicyPO.class);
        args.forEach(listQuery::setParameter);
        listQuery.setFirstResult((int) pageable.getOffset());
        listQuery.setMaxResults(pageable.getPageSize());
        String countSql = """
                SELECT
                	COUNT(DISTINCT a.id)
                FROM
                	AppAccessPolicyEntity a
                	INNER JOIN AppEntity app ON a.appId = app.id
                INNER JOIN
                (
                SELECT
                	id as id,
                	name as name
                FROM
                	UserGroupEntity UNION ALL
                SELECT
                	id as id,
                	name as name
                FROM
                	OrganizationEntity UNION ALL
                SELECT
                	id as id,
                	username AS name
                FROM
                	UserEntity
                	) subject ON a.subjectId = subject.id
                WHERE 1=1
                """;
        TypedQuery<Long> countQuery = entityManager.createQuery(countSql + whereSql, Long.class);
        args.forEach(countQuery::setParameter);
        return new PageImpl<>(listQuery.getResultList(), pageable, countQuery.getSingleResult());
    }
}
