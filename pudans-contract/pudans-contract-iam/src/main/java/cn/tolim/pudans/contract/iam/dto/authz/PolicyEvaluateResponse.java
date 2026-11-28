package cn.tolim.pudans.contract.iam.dto.authz;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Authorization evaluation result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyEvaluateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Final decision: ALLOW or DENY
    private String decision;

    // Whether the request is allowed
    private Boolean allowed;

    // Decision reason
    private String reason;

    // Codes of the policies that contributed to the decision
    private List<String> matchedPolicies;

    // Evaluation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime evaluatedAt;

    // Creates an allow result
    // Parameter reason: String
    // Parameter matchedPolicies: List<String>
    // Returns: PolicyEvaluateResponse
    public static PolicyEvaluateResponse allow(String reason, List<String> matchedPolicies) {
        return result("ALLOW", Boolean.TRUE, reason, matchedPolicies);
    }

    // Creates a deny result
    // Parameter reason: String
    // Parameter matchedPolicies: List<String>
    // Returns: PolicyEvaluateResponse
    public static PolicyEvaluateResponse deny(String reason, List<String> matchedPolicies) {
        return result("DENY", Boolean.FALSE, reason, matchedPolicies);
    }

    private static PolicyEvaluateResponse result(String decision, Boolean allowed, String reason,
                                                 List<String> matchedPolicies) {
        return PolicyEvaluateResponse.builder()
                .decision(decision)
                .allowed(allowed)
                .reason(reason)
                .matchedPolicies(matchedPolicies)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }
}
