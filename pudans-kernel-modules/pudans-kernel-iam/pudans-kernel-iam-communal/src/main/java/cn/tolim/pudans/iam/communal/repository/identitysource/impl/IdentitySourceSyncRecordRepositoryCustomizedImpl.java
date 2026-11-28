package cn.tolim.pudans.iam.communal.repository.identitysource.impl;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncRecordEntity;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceSyncRecordRepositoryCustomized;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;


@Repository
public class IdentitySourceSyncRecordRepositoryCustomizedImpl implements
        IdentitySourceSyncRecordRepositoryCustomized {

    private final JdbcTemplate jdbcTemplate;

    public IdentitySourceSyncRecordRepositoryCustomizedImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void batchSave(List<IdentitySourceSyncRecordEntity> list) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO pudans_identity_source_sync_record (id_, sync_history_id, action_type, object_id, object_name, object_type, status_,desc_,create_by,create_time,update_by,update_time,remark_,is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NotNull PreparedStatement ps, int i) throws SQLException {
                    IdentitySourceSyncRecordEntity entity = list.get(i);
                    ps.setString(1, entity.getId());
                    ps.setString(2, entity.getSyncHistoryId());
                    ps.setString(3, Objects.isNull(entity.getActionType()) ? null : entity.getActionType().getCode());
                    ps.setString(4, entity.getObjectId());
                    ps.setString(5, entity.getObjectName());
                    ps.setString(6, Objects.isNull(entity.getObjectType()) ? null : entity.getObjectType().getCode());
                    ps.setString(7, Objects.isNull(entity.getStatus()) ? null : entity.getStatus().getCode());
                    ps.setString(8, entity.getDesc());
                    ps.setString(9, entity.getCreateBy());
                    ps.setTimestamp(10, Timestamp.valueOf(entity.getCreateTime()));
                    ps.setString(11, entity.getUpdateBy());
                    ps.setTimestamp(12, Timestamp.valueOf(entity.getUpdateTime()));
                    ps.setString(13, entity.getRemark());
                    ps.setBoolean(14, false);
                    }

                    @Override
                    public int getBatchSize() {
                        return list.size();
                    }
                });
    }
}
