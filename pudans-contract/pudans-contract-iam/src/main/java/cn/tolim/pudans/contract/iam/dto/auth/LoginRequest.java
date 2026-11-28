package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Password login request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Username
    private String username;

    // Password
    private String password;

    // Application code the login is performed for
    private String appCode;

    // URL to return to after login
    private String targetUrl;

    // Client IP address
    private String ip;

    // Client user agent
    private String userAgent;
}
