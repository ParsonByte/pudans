package cn.tolim.pudans.audit.entity;

import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.converter.AuditTypeConverter;
import cn.tolim.pudans.audit.enums.converter.EventStatusConverter;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

@Getter
@Setter
@Entity
@Table(name = "pudans_audit")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AuditEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = -3119319193111206582L;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "session_id")
    private String sessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_")
    private List<Target> targets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_agent")
    private UserAgent userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geo_location")
    private GeoLocation geoLocation;

    @Convert(converter = AuditTypeConverter.class)
    @Column(name = "event_type")
    private EventType eventType;

    @Column(name = "event_param")
    private String eventParam;

    @Column(name = "event_content")
    private String eventContent;

    @Column(name = "event_result")
    private String eventResult;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Convert(converter = EventStatusConverter.class)
    @Column(name = "event_status")
    private EventStatus eventStatus;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "actor_type")
    private String actorType;

    @Column(name = "actor_auth_type")
    private String actorAuthType;
}