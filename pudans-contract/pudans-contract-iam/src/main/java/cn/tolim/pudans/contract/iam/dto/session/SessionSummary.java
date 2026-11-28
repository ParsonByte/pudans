package cn.tolim.pudans.contract.iam.dto.session;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Global login session summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Global login session ID
    private String sid;

    // User ID
    private String userId;

    // User type: USER or ADMIN
    private String userType;

    // Organization ID
    private String orgId;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Last seen time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSeenAt;

    // Expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    // Whether the session was revoked
    private Boolean revoked;

    // Revocation reason
    private String revokeReason;

    // Login IP address
    private String ip;

    // Login user agent
    private String userAgent;

    // Whether the session is active
    private Boolean active;

    // Application session caches bound to the session
    private List<ApplicationSessionSummary> applications;
}
