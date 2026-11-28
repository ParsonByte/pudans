package cn.tolim.pudans.iam.authorization.entity;

import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PolicyType;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_authz_policy")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AuthzPolicyEntity extends BaseEntity {

    // Unique policy name
    @Column(name = "name_", nullable = false, length = 100)
    private String name;

    // Unique policy code
    @Column(name = "code_", nullable = false, length = 100)
    private String code;

    // Policy type: SYSTEM or CUSTOM
    @Column(name = "type_", nullable = false, length = 32)
    private PolicyType type;

    // Policy document version
    @Column(name = "version_", length = 32)
    private String version;

    // CAM style policy document JSON
    @Column(name = "document_", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String document;

    // Status: ENABLED or DISABLED
    @Column(name = "status_", nullable = false, length = 32)
    private AuthorizationStatus status;

    // Policy description
    @Column(name = "desc_", length = 500)
    private String description;
}