package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "服务动作视图")
public class ServiceActionView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1504346146019066866L;

    // Action ID
    @Schema(description = "动作ID")
    private String id;

    // Application ID
    @Schema(description = "应用ID")
    private String appId;

    // Application code
    @Schema(description = "应用编码")
    private String appCode;

    // Action in the form {appCode}:{action}
    @Schema(description = "动作")
    private String action;

    // Related resource type
    @Schema(description = "关联资源类型")
    private String resourceType;

    // Action description
    @Schema(description = "动作描述")
    private String description;

    // Risk level
    @Schema(description = "风险等级")
    private String riskLevel;

    // Status
    @Schema(description = "状态")
    private String status;
}