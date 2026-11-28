package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "当前用户可访问的应用链接")
public class PortalAppView implements Serializable {

    @Serial
    private static final long serialVersionUID = -4549568321495739049L;

    // Application ID
    @Schema(description = "应用ID")
    private String appId;

    // Application code
    @Schema(description = "应用编码")
    private String appCode;

    // Application name
    @Schema(description = "应用名称")
    private String name;

    // Application icon
    @Schema(description = "应用图标")
    private String icon;

    // Application protocol
    @Schema(description = "应用协议")
    private String protocol;

    // Application type
    @Schema(description = "应用类型")
    private String type;

    // Application template
    @Schema(description = "应用模板")
    private String template;

    // URL the homepage should open for this application
    @Schema(description = "应用启动地址")
    private String launchUrl;

    // Application groups
    @Schema(description = "应用分组列表")
    private List<PortalAppGroupView> groups = new ArrayList<>();
}