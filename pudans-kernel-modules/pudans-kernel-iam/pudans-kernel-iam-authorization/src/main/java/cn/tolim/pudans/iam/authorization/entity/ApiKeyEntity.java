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

import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_api_key")
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class ApiKeyEntity extends BaseEntity {

    // Principal ID that owns the API key
    @Column(name = "principal_id", nullable = false, length = 64)
    private String principalId;

    // Access key ID, the public part of the API key
    @Column(name = "access_key_id", nullable = false, length = 100)
    private String accessKeyId;

    // Secret hash, the secret itself is never stored in plain text
    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;

    // Status: ENABLED or DISABLED
    @Column(name = "status_", nullable = false, length = 32)
    private AuthorizationStatus status;

    // Expiration time, null means never expires
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Checks whether the API key can be used at the given time
    // Parameter now: LocalDateTime
    // Returns: boolean
    public boolean isActiveAt(LocalDateTime now) {
        return AuthorizationStatus.ENABLED.equals(this.status)
                && (this.expiresAt == null || this.expiresAt.isAfter(now));
    }
}