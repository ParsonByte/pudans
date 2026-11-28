package cn.tolim.pudans.iam.protocol.cas.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "CAS 协议端点")
public class AppCasProtocolEndpoint implements Serializable {

    @Serial
    private static final long serialVersionUID = -2261602995152894964L;

    // CAS login endpoint
    @Schema(description = "CAS 登录端点")
    private String loginEndpoint;

    // CAS logout endpoint
    @Schema(description = "CAS 登出端点")
    private String logoutEndpoint;

    // CAS 2.0 service validation endpoint
    @Schema(description = "CAS 2.0 服务校验端点")
    private String serviceValidateEndpoint;

    // CAS 3.0 service validation endpoint
    @Schema(description = "CAS 3.0 服务校验端点")
    private String p3ServiceValidateEndpoint;

    // CAS 1.0 ticket validation endpoint
    @Schema(description = "CAS 1.0 票据校验端点")
    private String validateEndpoint;
}