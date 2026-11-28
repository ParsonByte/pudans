package cn.tolim.pudans.iam.communal.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "全局登录会话校验请求")
public class SessionValidateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6281483365080147473L;

    // Global login session ID
    @NotBlank(message = "sid must not be blank")
    @Schema(description = "全局登录会话ID")
    private String sid;

    // Application code that asks for validation
    @Schema(description = "应用编码")
    private String appCode;
}