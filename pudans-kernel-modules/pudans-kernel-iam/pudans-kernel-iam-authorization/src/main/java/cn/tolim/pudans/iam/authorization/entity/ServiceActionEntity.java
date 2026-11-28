package cn.tolim.pudans.iam.authorization.entity;

import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.RiskLevel;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_service_action")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class ServiceActionEntity extends BaseEntity {

    // Application ID that declares the action
    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    // Action name in the form {appCode}:{action}
    @Column(name = "action_", nullable = false, length = 150)
    private String action;

    // Related resource type in the form {appCode}:{resourceType}
    @Column(name = "resource_type", length = 150)
    private String resourceType;

    // Action description
    @Column(name = "desc_", length = 500)
    private String description;

    // Risk level: LOW, MEDIUM, HIGH or CRITICAL
    @Column(name = "risk_level", length = 32)
    private RiskLevel riskLevel;

    // Status: ENABLED or DISABLED
    @Column(name = "status_", nullable = false, length = 32)
    private AuthorizationStatus status;
}