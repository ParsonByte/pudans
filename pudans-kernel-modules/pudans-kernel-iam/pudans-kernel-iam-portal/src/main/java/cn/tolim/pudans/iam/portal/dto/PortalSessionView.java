package cn.tolim.pudans.iam.portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "全局登录会话信息")
public class PortalSessionView implements Serializable {

    @Serial
    private static final long serialVersionUID = -2835619574231984455L;

    // Whether the global login session is active
    @Schema(description = "全局登录会话是否有效")
    private Boolean active;

    // Session creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "会话创建时间")
    private LocalDateTime createdAt;

    // Last seen time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近活跃时间")
    private LocalDateTime lastSeenAt;

    // Session expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "会话过期时间")
    private LocalDateTime expiresAt;

    // Login IP address
    @Schema(description = "登录IP地址")
    private String loginIp;

    // Login user agent
    @Schema(description = "登录用户代理")
    private String userAgent;
}