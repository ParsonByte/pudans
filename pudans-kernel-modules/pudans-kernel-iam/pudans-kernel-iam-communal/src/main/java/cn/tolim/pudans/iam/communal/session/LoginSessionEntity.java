package cn.tolim.pudans.iam.communal.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_login_session")
public class LoginSessionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = -8453287406137068601L;

    // Global login session ID, the only login state shared by all applications
    @Id
    @Column(name = "sid", nullable = false, length = 64)
    private String sid;

    // User ID
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // User type: USER or ADMIN
    @Column(name = "user_type", nullable = false, length = 32)
    private String userType;

    // Organization ID
    @Column(name = "org_id", length = 64)
    private String orgId;

    // Creation time
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Last seen time
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    // Expiration time
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Revoked flag
    @Column(name = "revoked", nullable = false)
    private Boolean revoked;

    // Revoke reason
    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;

    // Login IP address
    @Column(name = "ip", length = 50)
    private String ip;

    // Login user agent
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // Checks whether the session is active at the given time
    // Parameter now: LocalDateTime
    // Returns: boolean
    public boolean isActiveAt(LocalDateTime now) {
        return !Boolean.TRUE.equals(this.revoked) && this.expiresAt != null && this.expiresAt.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.lastSeenAt == null) {
            this.lastSeenAt = now;
        }
        if (this.revoked == null) {
            this.revoked = Boolean.FALSE;
        }
    }
}