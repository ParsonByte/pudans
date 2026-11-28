package cn.tolim.pudans.iam.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "角色绑定视图")
public class RoleBindingView implements Serializable {

    @Serial
    private static final long serialVersionUID = 8431891684965119335L;

    // Binding ID
    @Schema(description = "绑定ID")
    private String id;

    // Bound role ID
    @Schema(description = "绑定角色ID")
    private String roleId;

    // Bound role code
    @Schema(description = "绑定角色编码")
    private String roleCode;

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