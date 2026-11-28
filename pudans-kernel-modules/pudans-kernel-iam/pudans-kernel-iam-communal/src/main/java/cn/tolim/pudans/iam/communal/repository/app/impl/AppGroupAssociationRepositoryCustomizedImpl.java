package cn.tolim.pudans.iam.communal.repository.app.impl;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.query.AppGroupAssociationListQueryParam;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepositoryCustomized;
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
public class AppGroupAssociationRepositoryCustomizedImpl implements
        AppGroupAssociationRepositoryCustomized {

    // EntityManager
    private final EntityManager entityManager;

    // Get application group application list
    // Parameter query: AppGroupAssociationListQueryParam
    // Parameter pageable: Pageable
    // Returns: Page
    @SuppressWarnings("DuplicatedCode")
    @Override
    public Page<AppEntity> getAppGroupAssociationList(AppGroupAssociationListQueryParam query,
                                                      Pageable pageable) {
        Map<String, Object> args = new HashMap<>();
        String hql = """
                SELECT DISTINCT
                	 app
                FROM
                	AppEntity app
                	LEFT JOIN AppGroupAssociationEntity ass ON app.id = ass.app.id
                WHERE
                	ass.groupId = :groupId
                """;
        String whereSql = "";
        // Username
        if (StringUtils.isNoneBlank(query.getAppName())) {
            whereSql += " AND app.name LIKE :name ";
            args.put("name", "%" + query.getAppName() + "%");
        }
        // Sort by creation time descending
        TypedQuery<AppEntity> listQuery = entityManager
                .createQuery(hql + whereSql + " ORDER BY app.createTime DESC", AppEntity.class);
        args.put("groupId", query.getId());
        args.forEach(listQuery::setParameter);
        listQuery.setFirstResult((int) pageable.getOffset());
        listQuery.setMaxResults(pageable.getPageSize());
        String countSql = """
                SELECT
                	COUNT(DISTINCT app.id)
                FROM
                	AppEntity app
                	LEFT JOIN AppGroupAssociationEntity ass ON app.id = ass.app.id
                WHERE
                	ass.groupId = :groupId
                """;
        TypedQuery<Long> countQuery = entityManager.createQuery(countSql + whereSql, Long.class);
        args.forEach(countQuery::setParameter);
        return new PageImpl<>(listQuery.getResultList(), pageable, countQuery.getSingleResult());
    }
}
