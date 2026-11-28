package cn.tolim.pudans.iam.authorization.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;


@Data
@Schema(description = "鉴权评估结果")
public class PolicyEvaluateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -2833508072773554935L;

    // Final decision: ALLOW or DENY, default is DENY
    @Schema(description = "最终决策：ALLOW 或 DENY")
    private String decision;

    // Whether the request is allowed
    @Schema(description = "请求是否允许")
    private Boolean allowed;

    // Decision reason
    @Schema(description = "决策原因")
    private String reason;

    // Codes of the policies that contributed to the decision
    @Schema(description = "匹配的策略编码")
    private List<String> matchedPolicies;

    // Evaluation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "评估时间")
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
        PolicyEvaluateResponse response = new PolicyEvaluateResponse();
        response.setDecision(decision);
        response.setAllowed(allowed);
        response.setReason(reason);
        response.setMatchedPolicies(matchedPolicies);
        response.setEvaluatedAt(LocalDateTime.now());
        return response;
    }
}