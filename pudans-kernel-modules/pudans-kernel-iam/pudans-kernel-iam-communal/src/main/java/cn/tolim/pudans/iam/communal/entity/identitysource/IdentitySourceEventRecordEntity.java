package cn.tolim.pudans.iam.communal.entity.identitysource;

import cn.tolim.pudans.iam.communal.enums.SyncStatus;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceActionType;
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
@Table(name = "pudans_identity_source_event_record")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class IdentitySourceEventRecordEntity extends BaseEntity {

    public static final String IDENTITY_SOURCE_ID_FIELD_NAME = "identitySourceId";

    public static final String ACTION_TYPE_FIELD_NAME = "actionType";

    public static final String OBJECT_TYPE_FIELD_NAME = "objectType";

    public static final String STATUS_FIELD_NAME = "status";

    // Identity source ID
    @Column(name = "identity_source_id")
    private String identitySourceId;

    // Action type
    @Column(name = "action_type")
    private IdentitySourceActionType actionType;

    // Object ID
    @Column(name = "object_id")
    private String objectId;

    // Object name
    @Column(name = "object_name")
    private String objectName;

    // Object type
    @Column(name = "object_type")
    private IdentitySourceObjectType objectType;

    // Event time
    @Column(name = "event_time")
    private LocalDateTime eventTime;

    // Event status
    @Column(name = "status_")
    private SyncStatus status;

    // Description
    @Column(name = "desc_")
    private String desc;
}
