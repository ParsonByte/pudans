package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Controlled application self-registration result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationRegistrationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Application code
    private String appCode;

    // Registration status: PENDING, ENABLED, DISABLED or REJECTED
    private String status;

    // Issued client ID
    private String clientId;

    // Issued client secret, returned only at registration time
    private String clientSecret;

    // Number of registered actions
    private Integer actionsRegistered;

    // Number of registered resource types
    private Integer resourceTypesRegistered;

    // Registration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registeredAt;
}
