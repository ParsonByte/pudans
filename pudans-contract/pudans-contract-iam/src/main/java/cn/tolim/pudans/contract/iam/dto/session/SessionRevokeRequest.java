package cn.tolim.pudans.contract.iam.dto.session;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Revoke one global login session request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionRevokeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Global login session ID
    private String sid;

    // Revocation reason
    private String reason;
}
