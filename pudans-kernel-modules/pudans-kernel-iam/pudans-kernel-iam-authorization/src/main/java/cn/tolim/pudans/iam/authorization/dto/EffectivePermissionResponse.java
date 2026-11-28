package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@Schema(description = "主体的有效权限")
public class EffectivePermissionResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -4714115832057346912L;

    // Requested principal type
    @Schema(description = "主体类型")
    private String principalType;

    // Requested principal ID
    @Schema(description = "主体ID")
    private String principalId;

    // Roles granted to the principal directly or through groups
    @Schema(description = "生效角色")
    private List<RoleSummary> roles = new ArrayList<>();

    // Policies granted to the principal directly or through groups and roles
    @Schema(description = "生效策略列表")
    private List<EffectivePolicyView> policies = new ArrayList<>();

    // Effective policy codes
    @Schema(description = "生效策略编码")
    private List<String> policyCodes = new ArrayList<>();
}