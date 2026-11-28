package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// One-time password login request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpLoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Email address or phone number
    private String recipient;

    // One-time verification code
    private String code;

    // Delivery channel: mail or sms
    private String channel;

    // Application code the login is performed for
    private String appCode;

    // URL to return to after login
    private String targetUrl;
}