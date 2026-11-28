package cn.tolim.pudans.iam.communal.repository.account.impl;

import cn.tolim.pudans.iam.communal.entity.account.po.UserPO;
import cn.tolim.pudans.iam.communal.entity.account.query.UserGroupMemberListQuery;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepositoryCustomized;
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
public class UserGroupMemberRepositoryCustomizedImpl implements
        UserGroupMemberRepositoryCustomized {
    private final EntityManager entityManager;

    // Get user group member list
    // Parameter query: UserGroupMemberListQuery
    // Parameter pageable: Pageable
    // Returns: Page
    @Override
    public Page<UserPO> getUserGroupMemberList(UserGroupMemberListQuery query, Pageable pageable) {
        Map<String, Object> args = new HashMap<>();
        String sql = """
                SELECT
                	%s
                FROM
                	UserGroupMemberEntity ugm
                	INNER JOIN UserEntity u ON ugm.userId = u.id
                	INNER JOIN UserGroupEntity ug ON ug.id = ugm.groupId
                	LEFT  JOIN OrganizationMemberEntity om ON u.id = om.userId
                    LEFT  JOIN OrganizationEntity o ON o.id = om.orgId
                """;
        args.put("userGroupMemberId", query.getId());
        StringBuilder whereSql = new StringBuilder("""
                WHERE
                	ugm.groupId = :userGroupMemberId
                	AND ug.id = :userGroupMemberId
                """);
        // Username
        if (StringUtils.isNoneBlank(query.getFullName())) {
            whereSql.append(" AND u.fullName LIKE :fullName ");
            args.put("fullName", "%" + query.getFullName() + "%");
        }
        String listSql = sql.formatted("""
                NEW cn.tolim.pudans.iam.communal.entity.account.po.UserPO(u,
                LISTAGG(o.displayPath , ',' ))
                """);
        TypedQuery<UserPO> listQuery = entityManager
                .createQuery(listSql + whereSql + " GROUP BY u.id ", UserPO.class);
        args.forEach(listQuery::setParameter);
        listQuery.setFirstResult((int) pageable.getOffset());
        listQuery.setMaxResults(pageable.getPageSize());
        String countSql = sql.formatted("COUNT(DISTINCT u.id)");
        TypedQuery<Long> countQuery = entityManager.createQuery(countSql + whereSql, Long.class);
        args.forEach(countQuery::setParameter);
        return new PageImpl<>(listQuery.getResultList(), pageable, countQuery.getSingleResult());
    }
}
