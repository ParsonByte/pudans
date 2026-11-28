package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Data
@Schema(description = "策略模拟器请求")
public class PolicySimulationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -6961784139858593524L;

    // Principal type, optional when inline policies are supplied
    @Schema(description = "主体类型")
    private String principalType;

    // Principal ID, optional when inline policies are supplied
    @Schema(description = "主体ID")
    private String principalId;

    // Inline policy documents to simulate, takes precedence over the principal
    @Schema(description = "内联策略文档")
    private List<PolicyDocument> policies;

    // Application code
    @NotBlank(message = "appCode must not be blank")
    @Schema(description = "应用编码")
    private String appCode;

    // Action in the form {appCode}:{action}
    @NotBlank(message = "action must not be blank")
    @Schema(description = "动作")
    private String action;

    // Resource in the form {appCode}:{resourceType}/{resourceId}
    @Schema(description = "资源")
    private String resource;

    // Organization ID the request is evaluated for
    @Schema(description = "组织ID")
    private String orgId;

    // Evaluation context, for example ip, mfa, time and tags
    @Schema(description = "评估上下文")
    private Map<String, Object> context;
}