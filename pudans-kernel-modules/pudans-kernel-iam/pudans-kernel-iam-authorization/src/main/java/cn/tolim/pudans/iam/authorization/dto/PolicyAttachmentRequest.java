package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "策略绑定请求")
public class PolicyAttachmentRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 2280876043707936622L;

    // Attached policy ID
    @NotBlank(message = "policyId must not be blank")
    @Schema(description = "已绑定策略ID")
    private String policyId;

    // Principal type: USER, USER_GROUP, ROLE, ORGANIZATION or CLIENT
    @NotBlank(message = "principalType must not be blank")
    @Schema(description = "主体类型")
    private String principalType;

    // Principal ID
    @NotBlank(message = "principalId must not be blank")
    @Schema(description = "主体ID")
    private String principalId;

    // Effective scope: application code, resource prefix or * for all
    @Schema(description = "生效范围")
    private String scope;
}