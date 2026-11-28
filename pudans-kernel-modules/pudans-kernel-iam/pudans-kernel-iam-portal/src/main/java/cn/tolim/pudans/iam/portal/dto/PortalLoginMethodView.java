package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "专用登录页可用的登录方式")
public class PortalLoginMethodView implements Serializable {

    @Serial
    private static final long serialVersionUID = 7985280225947031572L;

    // Login method code
    @Schema(description = "登录方式编码")
    private String code;

    // Login method display name
    @Schema(description = "登录方式显示名称")
    private String name;

    // Login method category: password, otp or mfa
    @Schema(description = "登录方式分类")
    private String category;

    // Whether the login method is enabled
    @Schema(description = "登录方式是否启用")
    private Boolean enabled;

    // Display order
    @Schema(description = "显示顺序")
    private Integer order;
}