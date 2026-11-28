package cn.tolim.pudans.iam.portal.dto;

import cn.tolim.pudans.iam.portal.constant.PortalConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "全局登出地址信息")
public class PortalLogoutUrlResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -5208106890148548637L;

    // Public global logout URL on the login page
    @Schema(description = "公开全局登出地址")
    private String logoutUrl;

    // Public login page URL
    @Schema(description = "公开登录页地址")
    private String loginUrl;

    // Public homepage URL
    @Schema(description = "公开首页地址")
    private String homeUrl;

    // Logout semantics, always global
    @Schema(description = "登出方式")
    private String method = PortalConstants.LOGOUT_METHOD_GLOBAL;
}