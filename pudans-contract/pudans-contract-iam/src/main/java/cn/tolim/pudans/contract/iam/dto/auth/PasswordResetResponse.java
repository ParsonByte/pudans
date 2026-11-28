package cn.tolim.pudans.contract.iam.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Password reset result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordResetResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Whether the password was reset
    private Boolean success;

    // Result message
    private String message;
}