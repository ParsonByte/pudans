package cn.tolim.pudans.iam.authorization.entity;

import cn.tolim.pudans.iam.authorization.enums.PolicyDecision;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.infra.repository.base.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.time.LocalDateTime;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_authz_log")
public class AuthzLogEntity extends IdEntity {

    @Serial
    private static final long serialVersionUID = -6258106235679785738L;

    // Principal type: USER, USER_GROUP, ROLE, ORGANIZATION or CLIENT
    @Column(name = "principal_type", nullable = false, length = 32)
    private PrincipalType principalType;

    // Principal ID
    @Column(name = "principal_id", nullable = false, length = 64)
    private String principalId;

    // Application code
    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    // Evaluated action in the form {appCode}:{action}
    @Column(name = "action_", nullable = false, length = 150)
    private String action;

    // Evaluated resource in the form {appCode}:{resourceType}/{resourceId}
    @Column(name = "resource_", length = 500)
    private String resource;

    // Final decision: ALLOW or DENY
    @Column(name = "decision", nullable = false, length = 16)
    private PolicyDecision decision;

    // Decision reason
    @Column(name = "reason", length = 500)
    private String reason;

    // Evaluation context JSON, for example IP, MFA state and organization
    @Column(name = "context")
    @JdbcTypeCode(SqlTypes.JSON)
    private String context;

    // Creation time
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}