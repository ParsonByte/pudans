package cn.tolim.pudans.iam.communal.session.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@Schema(description = "SSO 票据签发结果")
public class TicketIssueResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 3494667520531183369L;

    // One-time ticket
    @Schema(description = "一次性票据")
    private String ticket;

    // Application code
    @Schema(description = "应用编码")
    private String appCode;

    // Target URL the application should return to
    @Schema(description = "目标地址")
    private String targetUrl;

    // Ticket expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "票据过期时间")
    private LocalDateTime expiresAt;
}