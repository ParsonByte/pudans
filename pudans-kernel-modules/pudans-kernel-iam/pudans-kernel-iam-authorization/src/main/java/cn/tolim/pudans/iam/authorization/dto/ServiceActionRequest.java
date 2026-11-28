package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "服务动作声明请求")
public class ServiceActionRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -7432007311494162207L;

    // Application code that owns the action
    @NotBlank(message = "appCode must not be blank")
    @Schema(description = "应用编码")
    private String appCode;

    // Action name, the application prefix is added when omitted
    @NotBlank(message = "action must not be blank")
    @Schema(description = "动作，格式为 {appCode}:{action}")
    private String action;

    // Related resource type in the form {appCode}:{resourceType}
    @Schema(description = "关联资源类型")
    private String resourceType;

    // Action description
    @Schema(description = "动作描述")
    private String description;

    // Risk level: LOW, MEDIUM, HIGH or CRITICAL
    @Schema(description = "风险等级")
    private String riskLevel;

    // Status: ENABLED or DISABLED, defaults to ENABLED
    @Schema(description = "状态")
    private String status;
}