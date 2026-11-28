package cn.tolim.pudans.iam.appkernel.jwt.pojo;

import cn.tolim.pudans.iam.communal.enums.app.JwtBindingType;
import cn.tolim.pudans.iam.communal.enums.app.JwtIdTokenSubjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
@Schema(description = "保存 JWT 应用配置参数")
public class AppJwtSaveConfigParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 7257798528680745281L;

    // Login initiation URL
    @Schema(description = "登录发起登录URL")
    private String initLoginUrl;

    // JWT SSO URL in the business system. During single sign-on, the system sends id_token information to this address via [GET] with the parameter name id_token.
    // The business system can use id_token and the public key to obtain user information. If login starts in the business system (SP), the system validates the service parameter on the SP login address when present.
    // On success, the browser is redirected to this address with the id_token identity token.
    @NotBlank(message = "Redirect Uri不能为空")
    @Schema(description = "重定向地址")
    private String redirectUrl;

    // URL to redirect to after a successful JWT SSO in the business system, usually a secondary menu or similar.
    // If set, this URL is passed as target_uri during JWT SSO.
    // If not set, a target_uri request parameter from SSO is forwarded. Optional.
    @Schema(description = "目标链接地址")
    private String targetLinkUrl;

    // id_token sub type
    @NotNull(message = "id_token sub 类型不能为空")
    private JwtIdTokenSubjectType idTokenSubjectType;

    // id_token lifetime
    @NotNull(message = "id token有效期不能为空")
    @Max(value = 84600)
    @Min(value = 1)
    @Schema(description = "ID 令牌有效期")
    private Integer idTokenTimeToLive;

    // SSO binding type
    @NotNull(message = "SSO 绑定类型")
    private JwtBindingType bindingType;
}
