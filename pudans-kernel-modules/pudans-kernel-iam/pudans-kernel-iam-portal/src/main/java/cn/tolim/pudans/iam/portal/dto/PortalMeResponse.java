package cn.tolim.pudans.iam.portal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "当前门户用户与会话信息")
public class PortalMeResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -7701983253716640717L;

    // Whether the caller has an active global login session or a valid upstream authentication
    @Schema(description = "调用方是否已认证")
    private Boolean authenticated;

    // Current user information, null when the caller is anonymous
    @Schema(description = "当前用户信息")
    private PortalUserView user;

    // Current global login session information, null when only an upstream token authenticated the caller
    @Schema(description = "当前全局登录会话信息")
    private PortalSessionView session;
}