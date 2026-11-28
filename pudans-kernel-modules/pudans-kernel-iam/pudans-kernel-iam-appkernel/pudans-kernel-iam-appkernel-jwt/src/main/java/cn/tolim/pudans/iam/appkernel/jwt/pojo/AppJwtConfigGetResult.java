package cn.tolim.pudans.iam.appkernel.jwt.pojo;

import cn.tolim.pudans.iam.communal.enums.app.JwtBindingType;
import cn.tolim.pudans.iam.communal.enums.app.JwtIdTokenSubjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;


@Data
@Schema(description = "JWT 配置响应")
public class AppJwtConfigGetResult implements Serializable {
    // Application ID
    @Schema(description = "应用id")
    private String appId;

    // SSO login link
    @Schema(description = "SSO 登录链接")
    private String initLoginUrl;

    // JWT SSO URL in the business system. During single sign-on, the system sends id_token information to this address via [GET] with the parameter name id_token.
    // The business system can use id_token and the public key to obtain user information. If login starts in the business system (SP), the system validates the service parameter on the SP login address when present.
    // On success, the browser is redirected to this address with the id_token identity token.
    @Schema(description = "重定向地址")
    private String redirectUrl;

    // URL to redirect to after a successful JWT SSO in the business system, usually a secondary menu or similar. If set, this URL is passed as target_link_url during JWT SSO. If not set, a target_link_url request parameter from SSO is forwarded. Optional.
    @Schema(description = "目标链接地址")
    private String targetLinkUrl;

    @Schema(description = "SSO 绑定类型")
    private JwtBindingType bindingType;

    // id_token sub type
    private JwtIdTokenSubjectType idTokenSubjectType;

    // ID_token lifetime
    @Schema(description = "令牌过期时间（秒）")
    private String idTokenTimeToLive;

    // Protocol endpoint
    @Schema(description = "协议端点")
    private AppJwtProtocolEndpoint protocolEndpoint;
}
