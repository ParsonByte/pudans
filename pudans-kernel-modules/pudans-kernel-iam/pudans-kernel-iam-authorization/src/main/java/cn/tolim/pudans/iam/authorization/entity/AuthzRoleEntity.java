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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.type.SqlTypes;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_authz_role")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AuthzRoleEntity extends BaseEntity {

    // Unique role code
    @Column(name = "code_", nullable = false, length = 100)
    private String code;

    // Role name
    @Column(name = "name_", nullable = false, length = 100)
    private String name;

    // Role trust policy JSON, defines which principals may assume the role
    @Column(name = "trust_policy")
    @JdbcTypeCode(SqlTypes.JSON)
    private String trustPolicy;

    // Status: ENABLED or DISABLED
    @Column(name = "status_", nullable = false, length = 32)
    private AuthorizationStatus status;

    // Role description
    @Column(name = "desc_", length = 500)
    private String description;
}