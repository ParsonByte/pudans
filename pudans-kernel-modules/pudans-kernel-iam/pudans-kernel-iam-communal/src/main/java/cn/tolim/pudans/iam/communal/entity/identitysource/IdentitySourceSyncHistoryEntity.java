package cn.tolim.pudans.iam.communal.entity.identitysource;

import cn.tolim.pudans.iam.communal.enums.SyncStatus;
import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceObjectType;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@Table(name = "pudans_identity_source_sync_history")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class IdentitySourceSyncHistoryEntity extends BaseEntity {

    public static final String IDENTITY_SOURCE_ID_FIELD_NAME = "identitySourceId";

    public static final String TRIGGER_TYPE_FIELD_NAME = "triggerType";

    public static final String OBJECT_TYPE_FIELD_NAME = "objectType";

    public static final String STATUS_FIELD_NAME = "status";

    // Batch number
    @Column(name = "batch_")
    private String batch;

    // Identity source ID
    @Column(name = "identity_source_id")
    private String identitySourceId;

    // Created count
    @Column(name = "created_count")
    private Integer createdCount;

    // Updated count
    @Column(name = "updated_count")
    private Integer updatedCount;

    // Deleted count
    @Column(name = "deleted_count")
    private Integer deletedCount;

    // Skipped count
    @Column(name = "skipped_count")
    private Integer skippedCount;

    // Start time
    @Column(name = "start_time")
    private LocalDateTime startTime;

    // End time
    @Column(name = "end_time")
    private LocalDateTime endTime;

    // Object type (user, organization)
    @Column(name = "object_type")
    private IdentitySourceObjectType objectType;

    // Trigger type (manual, task, event)
    @Column(name = "trigger_type")
    private TriggerType triggerType;

    // Trigger type (manual, task, event)
    @Column(name = "status_")
    private SyncStatus status;
}
