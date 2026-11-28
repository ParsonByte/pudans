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
import java.util.List;

// Policy simulation result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicySimulationResponse implements Serializable {

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

    // Statements that contributed to the decision
    private List<MatchedStatementView> matchedStatements;

    // Evaluation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime evaluatedAt;
}