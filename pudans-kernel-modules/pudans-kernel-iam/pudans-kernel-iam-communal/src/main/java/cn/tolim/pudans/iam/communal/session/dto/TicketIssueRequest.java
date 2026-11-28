package cn.tolim.pudans.iam.communal.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "SSO 票据签发请求")
public class TicketIssueRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -5802626170240467223L;

    // Global login session ID of the current user
    @NotBlank(message = "sid must not be blank")
    @Schema(description = "全局登录会话ID")
    private String sid;

    // Application code the ticket is issued for
    @NotBlank(message = "appCode must not be blank")
    @Schema(description = "应用编码")
    private String appCode;

    // Target URL the application should return to after ticket exchange
    @Schema(description = "目标地址")
    private String targetUrl;
}