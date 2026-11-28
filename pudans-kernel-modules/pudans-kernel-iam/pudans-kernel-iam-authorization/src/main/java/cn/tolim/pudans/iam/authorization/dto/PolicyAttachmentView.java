package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "策略绑定视图")
public class PolicyAttachmentView implements Serializable {

    @Serial
    private static final long serialVersionUID = -6612151452495535262L;

    // Attachment ID
    @Schema(description = "绑定ID")
    private String id;

    // Attached policy ID
    @Schema(description = "已绑定策略ID")
    private String policyId;

    // Attached policy code
    @Schema(description = "已绑定策略编码")
    private String policyCode;

    // Principal type
    @Schema(description = "主体类型")
    private String principalType;

    // Principal ID
    @Schema(description = "主体ID")
    private String principalId;

    // Effective scope
    @Schema(description = "生效范围")
    private String scope;
}