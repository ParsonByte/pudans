package cn.tolim.pudans.contract.iam.dto.authz;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Registered resource type summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceTypeSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Resource type record ID
    private String id;

    // Application ID
    private String appId;

    // Application code
    private String appCode;

    // Resource type code in the form {appCode}:{resourceType}
    private String code;

    // Resource type name
    private String name;

    // Resource pattern
    private String pattern;

    // Resource type status: ENABLED or DISABLED
    private String status;
}
