package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "角色摘要")
public class RoleSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = -2648115948757649500L;

    // Role ID
    @Schema(description = "角色ID")
    private String id;

    // Role code
    @Schema(description = "角色编码")
    private String code;

    // Role name
    @Schema(description = "角色名称")
    private String name;

    // Status: ENABLED or DISABLED
    @Schema(description = "角色状态")
    private String status;

    // Role description
    @Schema(description = "角色描述")
    private String description;
}