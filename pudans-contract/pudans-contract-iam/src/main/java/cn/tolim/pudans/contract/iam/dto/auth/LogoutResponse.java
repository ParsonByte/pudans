package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Global logout result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogoutResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Whether the global logout completed
    private Boolean success;

    // Number of revoked global login sessions
    private Integer revokedSessionCount;

    // Number of applications notified through back-channel logout
    private Integer notifiedApplicationCount;

    // Revocation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime revokedAt;
}
