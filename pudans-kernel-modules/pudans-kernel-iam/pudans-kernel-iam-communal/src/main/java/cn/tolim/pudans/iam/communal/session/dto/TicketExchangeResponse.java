package cn.tolim.pudans.iam.communal.session.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@Schema(description = "SSO 票据交换结果")
public class TicketExchangeResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -8126654315655153287L;

    // Global login session ID the application session cache must be bound to
    @Schema(description = "全局登录会话ID")
    private String sid;

    // Application code
    @Schema(description = "应用编码")
    private String appCode;

    // User ID (subject)
    @Schema(description = "用户ID")
    private String userId;

    // Global login session expiration time, the application cache must not outlive it
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "全局登录会话过期时间")
    private LocalDateTime sessionExpiresAt;

    // User information and claims
    @Schema(description = "用户信息")
    private UserInfo user;
}