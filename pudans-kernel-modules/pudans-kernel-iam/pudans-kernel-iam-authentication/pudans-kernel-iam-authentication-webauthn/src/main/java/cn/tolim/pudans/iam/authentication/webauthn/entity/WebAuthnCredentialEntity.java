package cn.tolim.pudans.iam.authentication.webauthn.entity;

import cn.tolim.pudans.iam.authentication.webauthn.enums.WebAuthnCredentialStatus;
import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

// WebAuthn public key credential bound to an IAM user
@Accessors(chain = true)
@Getter
@Setter
@ToString
@Entity
@Table(name = "pudans_webauthn_credential",
        uniqueConstraints = @UniqueConstraint(name = "uk_pudans_webauthn_credential_id",
                columnNames = "credential_id"),
        indexes = @Index(name = "idx_pudans_webauthn_credential_user", columnList = "user_id"))
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class WebAuthnCredentialEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = -2076220939104357142L;

    // Owning user ID
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // Base64URL encoded credential ID
    @Column(name = "credential_id", nullable = false, length = 512)
    private String credentialId;

    // Base64URL encoded COSE public key
    @Column(name = "public_key_cose", nullable = false, columnDefinition = "text")
    private String publicKeyCose;

    // COSE signature algorithm identifier
    @Column(name = "cose_algorithm", nullable = false)
    private Integer coseAlgorithm;

    // Authenticator signature counter
    @Column(name = "sign_count", nullable = false)
    private Long signCount;

    // Comma separated authenticator transports
    @Column(name = "transports", length = 255)
    private String transports;

    // Base64URL encoded authenticator AAGUID
    @Column(name = "aaguid", length = 128)
    private String aaguid;

    // Attestation format used during registration
    @Column(name = "attestation_format", length = 32)
    private String attestationFormat;

    // Whether the credential is backup eligible
    @Column(name = "backup_eligible", nullable = false)
    private Boolean backupEligible;

    // Whether the credential is currently backed up
    @Column(name = "backup_state", nullable = false)
    private Boolean backupState;

    // Credential lifecycle status
    @Enumerated(EnumType.STRING)
    @Column(name = "status_", nullable = false, length = 16)
    private WebAuthnCredentialStatus status;

    // Time the credential was registered
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    // Time the credential was last used for a successful assertion
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}