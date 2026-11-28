package cn.tolim.pudans.iam.communal.repository.account.impl;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.po.OrganizationPO;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationRepositoryCustomized;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.AccountConstants.ORG_CACHE_NAME;


@Repository
@CacheConfig(cacheNames = {ORG_CACHE_NAME})
public class OrganizationRepositoryCustomizedImpl implements OrganizationRepositoryCustomized {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public OrganizationRepositoryCustomizedImpl(JdbcTemplate jdbcTemplate,
                                                EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    // Batch save
    // Parameter list: List
    @Override
    @CacheEvict(allEntries = true)
    public void batchSave(List<OrganizationEntity> list) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO pudans_organization (id_, code_, name_, parent_id, is_leaf, external_id, data_origin, type_, is_enabled, order_, path_, display_path, identity_source_id,create_by,create_time,update_by,update_time,remark_,is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NotNull PreparedStatement ps, int i) throws SQLException {
                        OrganizationEntity entity = list.get(i);
                        ps.setString(1, entity.getId());
                        ps.setString(2, entity.getCode());
                        ps.setString(3, entity.getName());
                        ps.setString(4, entity.getParentId());
                        ps.setBoolean(5, entity.getLeaf());
                        ps.setString(6, entity.getExternalId());
                        ps.setString(7, entity.getDataOrigin());
                        ps.setString(8, entity.getType().getCode());
                        ps.setBoolean(9, entity.getEnabled());
                        ps.setObject(10, !Objects.isNull(entity.getOrder()) ? entity.getOrder() : null);
                        ps.setString(11, entity.getPath());
                        ps.setString(12, entity.getDisplayPath());
                        ps.setString(13, entity.getIdentitySourceId());
                        ps.setString(14, entity.getCreateBy());
                        ps.setTimestamp(15, Timestamp.valueOf(entity.getCreateTime()));
                        ps.setString(16, entity.getUpdateBy());
                        ps.setTimestamp(17, Timestamp.valueOf(entity.getUpdateTime()));
                        ps.setString(18, entity.getRemark());
                        ps.setBoolean(19, false);
                    }

                    @Override
                    public int getBatchSize() {
                        return list.size();
                    }
                });
    }

    // Batch update
    // Parameter list: List
    @Override
    @CacheEvict(allEntries = true)
    public void batchUpdate(List<OrganizationEntity> list) {
        jdbcTemplate.batchUpdate(
                "UPDATE  pudans_organization SET code_=?, name_=?, parent_id=?, is_leaf=?, external_id=?, data_origin=?, type_=? ,is_enabled=?,order_=?, path_=?,display_path=?,identity_source_id=?,create_by=?,create_time=?,update_by=?,update_time=?,remark_=? WHERE id_=?",
                new BatchPreparedStatementSetter() {

                    @Override
                    public void setValues(@NotNull PreparedStatement ps, int i) throws SQLException {
                        OrganizationEntity entity = list.get(i);
                        ps.setString(1, entity.getCode());
                        ps.setString(2, entity.getName());
                        ps.setString(3, entity.getParentId());
                        ps.setBoolean(4, entity.getLeaf());
                        ps.setString(5, entity.getExternalId());
                        ps.setString(6, entity.getDataOrigin());
                        ps.setString(7, entity.getType().getCode());
                        ps.setBoolean(8, entity.getEnabled());
                        ps.setObject(9, Objects.isNull(entity.getOrder()) ? null : entity.getOrder());
                        ps.setString(10, entity.getPath());
                        ps.setString(11, entity.getDisplayPath());
                        ps.setString(12, entity.getIdentitySourceId());
                        ps.setString(13, entity.getCreateBy());
                        ps.setTimestamp(14, Timestamp.valueOf(entity.getCreateTime()));
                        ps.setString(15, entity.getUpdateBy());
                        ps.setTimestamp(16, Timestamp.valueOf(entity.getUpdateTime()));
                        ps.setString(17, entity.getRemark());
                        ps.setString(18, entity.getId());
                    }

                    @Override
                    public int getBatchSize() {
                        return list.size();
                    }
                });
    }

    @Override
    @Cacheable(key = "'po:'+#p0", unless = "#result==null")
    public List<OrganizationPO> getOrganizationList(@NotEmpty String userId) {
        String sql = """
                    SELECT
                        NEW cn.tolim.pudans.iam.communal.entity.account.po.OrganizationPO(
                            organization.id,
                            organization.name,
                            organization.code,
                            organization.type,
                            organization.parentId,
                            organization.path,
                            organization.displayPath,
                            organization.externalId,
                            organization.dataOrigin,
                            organization.identitySourceId,
                            organization.order,
                            organization.leaf,
                            organization.enabled,
                            organization.remark
                        )
                    FROM
                    	OrganizationEntity organization
                    	LEFT JOIN OrganizationMemberEntity om ON om.orgId = organization.id
                    WHERE
                        om.userId = :userId
                    GROUP BY
                    	organization
            """;
        return entityManager.createQuery(sql, OrganizationPO.class).setParameter("userId", userId)
                .getResultList();
    }
}
