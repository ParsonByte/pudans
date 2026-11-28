package cn.tolim.pudans.iam.authentication.dingtalk;

import cn.tolim.pudans.iam.authentication.core.client.IdentityProviderConfig;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;


@Data
@EqualsAndHashCode(callSuper = true)
public class DingTalkIdentityProviderOAuth2Config extends IdentityProviderConfig {
    @Serial
    private static final long serialVersionUID = -6850223527422243076L;

    // Application key
    @NotBlank(message = "应用AppKey不能为空")
    private String appKey;

    // Application Secret
    @NotBlank(message = "应用AppSecret不能为空")
    private String appSecret;

    // Organization the user must select
    private String corpId;
}
