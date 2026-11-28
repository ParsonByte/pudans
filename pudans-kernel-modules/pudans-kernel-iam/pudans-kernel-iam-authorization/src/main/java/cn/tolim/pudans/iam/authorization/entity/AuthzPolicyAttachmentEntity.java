package cn.tolim.pudans.iam.authorization.entity;

import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
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
@Table(name = "pudans_authz_policy_attachment")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class AuthzPolicyAttachmentEntity extends BaseEntity {

    // Attached policy ID
    @Column(name = "policy_id", nullable = false, length = 64)
    private String policyId;

    // Principal type: USER, USER_GROUP, ROLE, ORGANIZATION or CLIENT
    @Column(name = "principal_type", nullable = false, length = 32)
    private PrincipalType principalType;

    // Principal ID
    @Column(name = "principal_id", nullable = false, length = 64)
    private String principalId;

    // Effective scope (application code or resource prefix, * means all)
    @Column(name = "scope_", length = 255)
    private String scope;
}