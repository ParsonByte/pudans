package cn.tolim.pudans.iam.communal.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "SSO 票据交换请求")
public class TicketExchangeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1745204151076035276L;

    // One-time ticket
    @NotBlank(message = "ticket must not be blank")
    @Schema(description = "一次性票据")
    private String ticket;

    // Application code that exchanges the ticket
    @NotBlank(message = "appCode must not be blank")
    @Schema(description = "应用编码")
    private String appCode;
}