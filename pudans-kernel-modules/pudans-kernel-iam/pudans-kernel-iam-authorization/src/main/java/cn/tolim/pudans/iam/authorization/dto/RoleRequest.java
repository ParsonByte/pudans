package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "角色创建或更新请求")
public class RoleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -162022728344384966L;

    // Unique role code
    @NotBlank(message = "code must not be blank")
    @Schema(description = "唯一角色编码")
    private String code;

    // Role name
    @NotBlank(message = "name must not be blank")
    @Schema(description = "角色名称")
    private String name;

    // Role trust policy JSON document
    @Schema(description = "角色信任策略 JSON 文档")
    private String trustPolicy;

    // Role status: ENABLED or DISABLED, defaults to ENABLED
    @Schema(description = "角色状态")
    private String status;

    // Role description
    @Schema(description = "角色描述")
    private String description;
}