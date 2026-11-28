package cn.tolim.pudans.contract.iam.dto.authz;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

// Authorization decision log entry
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthzLogView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Decision log ID
    private String id;

    // Principal type
    private String principalType;

    // Principal ID
    private String principalId;

    // Application code
    private String appCode;

    // Action
    private String action;

    // Resource
    private String resource;

    // Decision: ALLOW or DENY
    private String decision;

    // Decision reason
    private String reason;

    // Serialized decision context
    private String context;

    // Decision time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}