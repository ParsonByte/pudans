package cn.tolim.pudans.contract.iam.dto.session;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Back-channel logout notification payload
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackchannelLogoutRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Signed logout token, when the token is sent as a string
    private String logoutToken;

    // Token issuer
    private String iss;

    // Token audience, the application code
    private String aud;

    // Global login session ID to invalidate
    private String sid;

    // User ID (subject)
    private String sub;

    // Token issue time as epoch seconds
    private Long iat;

    // Token identifier used for replay protection
    private String jti;

    // Logout event claims
    private Map<String, Object> events;
}
