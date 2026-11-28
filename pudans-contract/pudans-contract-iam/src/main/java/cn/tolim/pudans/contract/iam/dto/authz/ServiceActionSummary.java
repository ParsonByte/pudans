package cn.tolim.pudans.contract.iam.dto.authz;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Registered service action summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceActionSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Action record ID
    private String id;

    // Application ID
    private String appId;

    // Application code
    private String appCode;

    // Action in the form {appCode}:{action}
    private String action;

    // Related resource type code
    private String resourceType;

    // Action description
    private String description;

    // Risk level: LOW, MEDIUM, HIGH or CRITICAL
    private String riskLevel;

    // Action status: ENABLED or DISABLED
    private String status;
}
