package cn.tolim.pudans.iam.communal.repository.identitysource.impl;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEventRecordEntity;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceEventRecordRepositoryCustomized;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;


@SuppressWarnings("DuplicatedCode")
@Repository
public class IdentitySourceEventRecordRepositoryCustomizedImpl implements
        IdentitySourceEventRecordRepositoryCustomized {

    private final JdbcTemplate jdbcTemplate;

    public IdentitySourceEventRecordRepositoryCustomizedImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void batchSave(List<IdentitySourceEventRecordEntity> list) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO pudans_identity_source_event_record (id_, identity_source_id, action_type, object_id, object_name, object_type, status_,event_time,desc_,create_by,create_time,update_by,update_time,remark_,is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NotNull PreparedStatement ps, int i) throws SQLException {
                        IdentitySourceEventRecordEntity entity = list.get(i);
                        ps.setString(1, entity.getId());
                        ps.setString(2, entity.getIdentitySourceId());
                        ps.setString(3, Objects.isNull(entity.getActionType()) ? null : entity.getActionType().getCode());
                        ps.setString(4, entity.getObjectId());
                        ps.setString(5, entity.getObjectName());
                        ps.setString(6, Objects.isNull(entity.getObjectType()) ? null : entity.getObjectType().getCode());
                        ps.setString(7, Objects.isNull(entity.getStatus()) ? null : entity.getStatus().getCode());
                        ps.setTimestamp(8, Timestamp.valueOf(entity.getEventTime()));
                        ps.setString(9, entity.getDesc());
                        ps.setString(10, entity.getCreateBy());
                        ps.setTimestamp(11, Timestamp.valueOf(entity.getCreateTime()));
                        ps.setString(12, entity.getUpdateBy());
                        ps.setTimestamp(13, Timestamp.valueOf(entity.getUpdateTime()));
                        ps.setString(14, entity.getRemark());
                        ps.setBoolean(15, false);
                    }

                    @Override
                    public int getBatchSize() {
                        return list.size();
                    }
                });
    }

}
