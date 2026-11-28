package cn.tolim.pudans.iam.communal.session.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@Schema(description = "全局登录会话校验结果")
public class SessionValidateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -2034614827867296105L;

    // Whether the global login session is active
    @Schema(description = "全局登录会话是否有效")
    private Boolean active;

    // User ID (subject)
    @Schema(description = "用户ID")
    private String sub;

    // User type: USER or ADMIN
    @Schema(description = "用户类型")
    private String userType;

    // Organization ID
    @Schema(description = "组织ID")
    private String orgId;

    // Application code the validation was requested for
    @Schema(description = "应用编码")
    private String appCode;

    // Global login session expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "会话过期时间")
    private LocalDateTime expiresAt;

    // Reason when the session is not active
    @Schema(description = "失效原因")
    private String reason;

    // Creates an active result
    // Parameter sub: String
    // Parameter userType: String
    // Parameter orgId: String
    // Parameter appCode: String
    // Parameter expiresAt: LocalDateTime
    // Returns: SessionValidateResponse
    public static SessionValidateResponse active(String sub, String userType, String orgId,
                                                 String appCode, LocalDateTime expiresAt) {
        SessionValidateResponse response = new SessionValidateResponse();
        response.setActive(Boolean.TRUE);
        response.setSub(sub);
        response.setUserType(userType);
        response.setOrgId(orgId);
        response.setAppCode(appCode);
        response.setExpiresAt(expiresAt);
        return response;
    }

    // Creates an inactive result
    // Parameter reason: String
    // Returns: SessionValidateResponse
    public static SessionValidateResponse inactive(String reason) {
        SessionValidateResponse response = new SessionValidateResponse();
        response.setActive(Boolean.FALSE);
        response.setReason(reason);
        return response;
    }
}