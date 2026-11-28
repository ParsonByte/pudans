package cn.tolim.pudans.contract.iam.dto.authz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

// Policy simulation request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicySimulationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Principal type: USER, USER_GROUP, ROLE, ORGANIZATION or CLIENT
    private String principalType;

    // Principal ID
    private String principalId;

    // Policies to simulate, used instead of the attached policies when present
    private List<PolicyDocument> policies;

    // Application code
    private String appCode;

    // Action in the form {appCode}:{action}
    private String action;

    // Resource in the form {appCode}:{resourceType}/{resourceId}
    private String resource;

    // Organization ID the request is evaluated for
    private String orgId;

    // Evaluation context such as ip, mfa, time and tags
    private Map<String, Object> context;
}