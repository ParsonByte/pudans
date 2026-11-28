package cn.tolim.pudans.iam.appkernel.oidc.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "协议端点")
public class AppOidcProtocolEndpoint implements Serializable {

    @Serial
    private static final long serialVersionUID = -2261602995152894964L;
    // oidcIssuer
    @Schema(description = "签发方")
    private String issuer;

    // discoveryEndpoint
    @Schema(description = "发现端点")
    private String discoveryEndpoint;

    // UserinfoEndpoint
    @Schema(description = "用户信息端点")
    private String userinfoEndpoint;

    // jwksEndpoint
    @Schema(description = "JWKS 端点")
    private String jwksEndpoint;

    // revokeEndpoint
    @Schema(description = "撤销端点")
    private String revokeEndpoint;

    // tokenEndpoint
    @Schema(description = "令牌端点")
    private String tokenEndpoint;

    // authorizationEndpoint
    @Schema(description = "授权端点")
    private String authorizationEndpoint;

    // endSessionEndpoint
    @Schema(description = "结束会话端点")
    private String endSessionEndpoint;
}
