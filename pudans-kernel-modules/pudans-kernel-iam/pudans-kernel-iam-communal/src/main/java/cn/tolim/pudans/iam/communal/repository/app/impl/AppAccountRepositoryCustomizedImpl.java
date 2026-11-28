package cn.tolim.pudans.iam.communal.repository.app.impl;

import cn.tolim.pudans.iam.communal.entity.app.po.AppAccountPO;
import cn.tolim.pudans.iam.communal.entity.app.query.AppAccountQueryParam;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepositoryCustomized;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;


@Repository
@AllArgsConstructor
public class AppAccountRepositoryCustomizedImpl implements AppAccountRepositoryCustomized {

    // EntityManager
    private final EntityManager entityManager;

    // Get application account list
    // Parameter pageable: Pageable
    // Parameter query: AppAccountQueryParam
    // Returns: Page
    @Override
    public Page<AppAccountPO> getAppAccountList(AppAccountQueryParam query, Pageable pageable) {
        Map<String, Object> args = new HashMap<>();
        String hql = """
                SELECT
                	NEW cn.tolim.pudans.iam.communal.entity.app.po.AppAccountPO(a.id,
                	a.appId,
                	a.userId,
                	a.account,
                	a.createTime,
                	a.defaulted,
                	u.username,
                	p.name,
                	p.type,
                	p.template,
                	p.protocol)
                FROM
                	AppAccountEntity a
                	LEFT JOIN UserEntity u ON a.userId = u.id
                	LEFT JOIN AppEntity p ON a.appId = p.id
                	WHERE 1=1
                """;
        String whereSql = "";
        // Username
        if (StringUtils.isNoneBlank(query.getUsername())) {
            whereSql += " AND u.username LIKE :username";
            args.put("username", "%" + query.getUsername() + "%");
        }
        // User ID
        if (StringUtils.isNoneBlank(query.getUserId())) {
            whereSql += " AND u.id = :userId";
            args.put("userId", query.getUserId());
        }
        // Account name
        if (StringUtils.isNoneBlank(query.getAccount())) {
            whereSql += " AND a.account LIKE :account";
            args.put("account", "%" + query.getAccount() + "%");
        }

        // Application ID
        if (StringUtils.isNoneBlank(query.getAppId())) {
            whereSql += " AND a.appId = :appId";
            args.put("appId", query.getAppId());
        }

        // Application name
        if (StringUtils.isNotBlank(query.getAppName())) {
            whereSql += " AND p.name LIKE :appName";
            args.put("appName", "%" + query.getAppName() + "%");
        }
        // Sort by creation time descending
        TypedQuery<AppAccountPO> listQuery = entityManager.createQuery(hql + whereSql,
                AppAccountPO.class);
        args.forEach(listQuery::setParameter);
        listQuery.setFirstResult((int) pageable.getOffset());
        listQuery.setMaxResults(pageable.getPageSize());
        String countSql = """
                SELECT
                	COUNT(DISTINCT a.id)
                FROM
                	AppAccountEntity a
                	LEFT JOIN UserEntity u ON a.userId = u.id
                	LEFT JOIN AppEntity p ON a.appId = p.id
                	WHERE 1=1
                """;
        TypedQuery<Long> countQuery = entityManager.createQuery(countSql + whereSql, Long.class);
        args.forEach(countQuery::setParameter);
        return new PageImpl<>(listQuery.getResultList(), pageable, countQuery.getSingleResult());
    }
}
