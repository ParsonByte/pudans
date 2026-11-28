package cn.tolim.pudans.contract.iam.dto.session;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Revoke one global login session result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionRevokeResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Whether a session was revoked
    private Boolean revoked;

    // Global login session ID
    private String sid;

    // Revocation reason
    private String reason;

    // Revocation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime revokedAt;
}
