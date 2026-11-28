package cn.tolim.pudans.iam.authorization.entity;

import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
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
@Table(name = "pudans_resource_type")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class ResourceTypeEntity extends BaseEntity {

    // Application ID that declares the resource type
    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    // Resource type code in the form {appCode}:{resourceType}
    @Column(name = "code_", nullable = false, length = 150)
    private String code;

    // Resource type name
    @Column(name = "name_", nullable = false, length = 100)
    private String name;

    // Resource pattern, for example org/{orgId}/{appCode}/{resourceType}/{resourceId}
    @Column(name = "pattern_", nullable = false, length = 255)
    private String pattern;

    // Status: ENABLED or DISABLED
    @Column(name = "status_", nullable = false, length = 32)
    private AuthorizationStatus status;
}