package cn.tolim.pudans.contract.iam.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// MFA challenge request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MfaChallengeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Username the challenge belongs to
    private String username;

    // Application code the login is performed for
    private String appCode;
}