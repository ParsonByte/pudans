package cn.tolim.pudans.iam.authentication.totp.entity;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpStatus;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.time.Instant;
import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

// TOTP credential bound to an IAM user
@Accessors(chain = true)
@Getter
@Setter
@ToString
@Entity
@Table(name = "pudans_totp_credential", indexes = {
        @Index(name = "idx_pudans_totp_credential_user", columnList = "user_id,status_")})
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class TotpCredentialEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 3091472386017526591L;

    // Owning user ID
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // User supplied device or account name
    @Column(name = "name_", length = 128)
    private String name;

    // Spissatus cipher algorithm ID
    @Column(name = "secret_algorithm", nullable = false, length = 64)
    private String secretAlgorithm;

    // Spissatus key ID protecting the secret
    @Column(name = "secret_key_id", nullable = false, length = 128)
    private String secretKeyId;

    // Spissatus key version protecting the secret
    @Column(name = "secret_key_version", nullable = false)
    private Long secretKeyVersion;

    // Base64 encoded AEAD nonce
    @Column(name = "secret_nonce", nullable = false, length = 128)
    private String secretNonce;

    // Base64 encoded ciphertext
    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "text")
    private String secretCiphertext;

    // Base64 encoded authentication tag
    @Column(name = "secret_tag", nullable = false, length = 128)
    private String secretTag;

    // TOTP hash algorithm
    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm_", nullable = false, length = 16)
    private TotpAlgorithm algorithm;

    // Number of digits in generated codes
    @Column(name = "digits_", nullable = false)
    private Integer digits;

    // Time step size in seconds
    @Column(name = "period_seconds", nullable = false)
    private Integer periodSeconds;

    // Credential lifecycle status
    @Enumerated(EnumType.STRING)
    @Column(name = "status_", nullable = false, length = 16)
    private TotpStatus status;

    // Time the enrollment code was successfully confirmed
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // Last accepted time step, used for replay protection
    @Column(name = "last_used_step")
    private Long lastUsedStep;

    // Time the credential was last used for a successful verification
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // Consecutive failed verification attempts
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts;

    // Time until which verification attempts are rejected
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // Returns true when the credential is currently locked
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
    }
}