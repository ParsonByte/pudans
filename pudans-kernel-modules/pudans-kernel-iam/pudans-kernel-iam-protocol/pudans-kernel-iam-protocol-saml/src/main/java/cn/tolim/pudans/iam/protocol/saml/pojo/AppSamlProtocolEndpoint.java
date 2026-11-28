package cn.tolim.pudans.iam.protocol.saml.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "SAML 协议端点")
public class AppSamlProtocolEndpoint implements Serializable {

    @Serial
    private static final long serialVersionUID = -2261602995152894964L;

    // Identity provider entity ID
    @Schema(description = "身份提供商实体ID")
    private String idpEntityId;

    // Identity provider metadata endpoint
    @Schema(description = "身份提供商元数据端点")
    private String metadataEndpoint;

    // Identity provider single sign-on endpoint
    @Schema(description = "身份提供商单点登录端点")
    private String idpSsoEndpoint;

    // Identity provider single logout endpoint
    @Schema(description = "身份提供商单点登出端点")
    private String idpSloEndpoint;
}