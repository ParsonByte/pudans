package cn.tolim.pudans.iam.authorization.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Data
@Schema(description = "策略模拟器结果")
public class PolicySimulationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -3140298146103034967L;

    // Final decision: ALLOW or DENY
    @Schema(description = "最终决策：ALLOW 或 DENY")
    private String decision;

    // Whether the simulated request is allowed
    @Schema(description = "请求是否允许")
    private Boolean allowed;

    // Decision reason
    @Schema(description = "决策原因")
    private String reason;

    // Codes of the policies that contributed to the decision
    @Schema(description = "匹配的策略编码")
    private List<String> matchedPolicies = new ArrayList<>();

    // Statements that matched the simulated request
    @Schema(description = "匹配的语句")
    private List<MatchedStatementView> matchedStatements = new ArrayList<>();

    // Simulation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "模拟时间")
    private LocalDateTime evaluatedAt;
}