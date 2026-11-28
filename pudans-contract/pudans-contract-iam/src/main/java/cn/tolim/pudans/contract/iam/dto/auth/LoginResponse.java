package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Password login result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Global login session ID
    private String sid;

    // Authenticated user information
    private UserInfo user;

    // Global login session expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    // Whether MFA verification is required before the session is usable
    private Boolean mfaRequired;

    // MFA challenge ID when MFA is required
    private String mfaChallengeId;

    // Application code the login was performed for
    private String appCode;

    // URL to return to after login
    private String redirectUrl;
}
