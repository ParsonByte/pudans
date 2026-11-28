package cn.tolim.pudans.contract.iam.dto.session;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Global login session validation result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionValidateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Whether the global login session is active
    private Boolean active;

    // User ID (subject)
    private String sub;

    // User type: USER or ADMIN
    private String userType;

    // Organization ID
    private String orgId;

    // Application code the validation was requested for
    private String appCode;

    // Global login session expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    // Reason when the session is not active
    private String reason;

    // Creates an active result
    // Parameter sub: String
    // Parameter userType: String
    // Parameter orgId: String
    // Parameter appCode: String
    // Parameter expiresAt: LocalDateTime
    // Returns: SessionValidateResponse
    public static SessionValidateResponse active(String sub, String userType, String orgId,
                                                 String appCode, LocalDateTime expiresAt) {
        return SessionValidateResponse.builder()
                .active(Boolean.TRUE)
                .sub(sub)
                .userType(userType)
                .orgId(orgId)
                .appCode(appCode)
                .expiresAt(expiresAt)
                .build();
    }

    // Creates an inactive result
    // Parameter reason: String
    // Returns: SessionValidateResponse
    public static SessionValidateResponse inactive(String reason) {
        return SessionValidateResponse.builder()
                .active(Boolean.FALSE)
                .reason(reason)
                .build();
    }
}
