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
@Table(name = "pudans_sso_ticket")
public class SsoTicketEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 4387516058305202748L;

    // One-time ticket
    @Id
    @Column(name = "ticket", nullable = false, length = 64)
    private String ticket;

    // Application ID
    @Column(name = "app_id", length = 64)
    private String appId;

    // Application code
    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    // User ID
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // Global login session ID
    @Column(name = "sid", nullable = false, length = 64)
    private String sid;

    // Target URL to return to after ticket exchange
    @Column(name = "target_url", length = 1000)
    private String targetUrl;

    // Creation time
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Expiration time
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Usage time, null when unused
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // Checks whether the ticket can still be exchanged at the given time
    // Parameter now: LocalDateTime
    // Returns: boolean
    public boolean isExchangeableAt(LocalDateTime now) {
        return this.usedAt == null && this.expiresAt != null && this.expiresAt.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}