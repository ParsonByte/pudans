package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "主体的生效策略")
public class EffectivePolicyView implements Serializable {

    @Serial
    private static final long serialVersionUID = 6443721190473269372L;

    // Policy ID
    @Schema(description = "策略ID")
    private String policyId;

    // Policy code
    @Schema(description = "策略编码")
    private String policyCode;

    // Policy name
    @Schema(description = "策略名称")
    private String policyName;

    // Effective scope of the attachment
    @Schema(description = "生效范围")
    private String scope;

    // Principal type the policy is attached to
    @Schema(description = "来源主体类型")
    private String sourcePrincipalType;

    // Principal ID the policy is attached to
    @Schema(description = "来源主体ID")
    private String sourcePrincipalId;
}