package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "策略创建或更新请求")
public class PolicyRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6034719764520667631L;

    // Policy name
    @NotBlank(message = "name must not be blank")
    @Schema(description = "策略名称")
    private String name;

    // Unique policy code
    @NotBlank(message = "code must not be blank")
    @Schema(description = "唯一策略编码")
    private String code;

    // Policy type: SYSTEM or CUSTOM, defaults to CUSTOM
    @Schema(description = "策略类型")
    private String type;

    // Policy document version
    @Schema(description = "策略文档版本")
    private String version;

    // CAM style policy document
    @NotNull(message = "document must not be null")
    @Schema(description = "CAM 式策略文档")
    private PolicyDocument document;

    // Policy status: ENABLED or DISABLED, defaults to ENABLED
    @Schema(description = "策略状态")
    private String status;

    // Policy description
    @Schema(description = "策略描述")
    private String description;
}