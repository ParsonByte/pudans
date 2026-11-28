package cn.tolim.pudans.iam.communal.session;

import cn.tolim.pudans.iam.infra.repository.base.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDateTime;


@Getter
@Setter
@ToString
@Entity
@Accessors(chain = true)
@Table(name = "pudans_app_session")
public class AppSessionEntity extends IdEntity {

    @Serial
    private static final long serialVersionUID = -4112547182876305716L;

    // Global login session ID the application session cache is bound to
    @Column(name = "sid", nullable = false, length = 64)
    private String sid;

    // Application ID
    @Column(name = "app_id", length = 64)
    private String appId;

    // Application code
    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    // User ID
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // Creation time
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Last seen time
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    // Expiration time, never later than the global login session expiration
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Revoked flag
    @Column(name = "revoked", nullable = false)
    private Boolean revoked;

    // Last back-channel logout notification time
    @Column(name = "last_backchannel_at")
    private LocalDateTime lastBackchannelAt;

    // Checks whether the application session cache is active at the given time
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