package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "登录页品牌配置")
public class PortalBrandingView implements Serializable {

    @Serial
    private static final long serialVersionUID = 6329779486401993579L;

    // Branding title
    @Schema(description = "品牌标题")
    private String title;

    // Logo URL
    @Schema(description = "Logo 地址")
    private String logoUrl;

    // Favicon URL
    @Schema(description = "站点图标地址")
    private String faviconUrl;

    // Background image URL
    @Schema(description = "背景图地址")
    private String backgroundUrl;
}