package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "登录页展示的社交或企业身份提供商")
public class PortalIdentityProviderView implements Serializable {

    @Serial
    private static final long serialVersionUID = -3324782881064227704L;

    // Identity provider code
    @Schema(description = "身份提供商编码")
    private String code;

    // Identity provider display name
    @Schema(description = "身份提供商显示名称")
    private String name;

    // Identity provider platform type
    @Schema(description = "身份提供商平台类型")
    private String type;

    // Identity provider category
    @Schema(description = "身份提供商分类")
    private String category;
}