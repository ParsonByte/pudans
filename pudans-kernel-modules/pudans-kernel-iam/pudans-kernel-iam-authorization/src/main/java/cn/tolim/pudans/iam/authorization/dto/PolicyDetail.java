package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;


@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "策略详情")
public class PolicyDetail extends PolicySummary {

    @Serial
    private static final long serialVersionUID = 4517310228723620504L;

    // Parsed CAM style policy document
    @Schema(description = "CAM 式策略文档")
    private PolicyDocument document;

    // Number of principals the policy is attached to
    @Schema(description = "绑定数量")
    private Long attachmentCount;
}