package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// MFA verification request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MfaVerifyRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // MFA challenge ID returned by login
    private String challengeId;

    // One-time verification code
    private String code;

    // Username the challenge belongs to
    private String username;

    // Application code the login is performed for
    private String appCode;
}
