package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Global logout request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogoutRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Global login session ID
    private String sid;

    // User ID for user-wide logout, optional when sid is given
    private String userId;

    // Logout reason
    private String reason;

    // Client IP address
    private String ip;

    // Client user agent
    private String userAgent;
}
