package cn.tolim.pudans.contract.iam.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Password reset request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordResetRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Username, email address or phone number
    private String identifier;

    // Delivery channel: mail or sms
    private String channel;

    // One-time reset token or code
    private String token;

    // New password
    private String newPassword;
}