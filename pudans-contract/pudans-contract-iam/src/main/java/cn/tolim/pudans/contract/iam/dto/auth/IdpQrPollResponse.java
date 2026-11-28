package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Identity provider QR login poll result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdpQrPollResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Login state: pending, success, expired or failed
    private String status;

    // Result message
    private String message;

    // Completed login session
    private LoginResponse login;
}