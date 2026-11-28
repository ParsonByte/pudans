package cn.tolim.pudans.contract.iam.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

// MFA challenge result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MfaChallengeResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // MFA challenge ID used by the verification request
    private String challengeId;

    // Username the challenge belongs to
    private String username;

    // MFA method: TOTP, SMS, MAIL or WEBAUTHN
    private String method;

    // Challenge expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}