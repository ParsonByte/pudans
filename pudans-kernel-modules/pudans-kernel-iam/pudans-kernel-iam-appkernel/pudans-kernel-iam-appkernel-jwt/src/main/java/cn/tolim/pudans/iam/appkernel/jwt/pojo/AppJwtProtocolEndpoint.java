package cn.tolim.pudans.iam.appkernel.jwt.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "协议端点")
public class AppJwtProtocolEndpoint implements Serializable {

    @Serial
    private static final long serialVersionUID = -2261602995152894964L;

    // IdP SSO endpoint
    @Schema(description = "IDP SSO 端点")
    private String idpSsoEndpoint;
}
