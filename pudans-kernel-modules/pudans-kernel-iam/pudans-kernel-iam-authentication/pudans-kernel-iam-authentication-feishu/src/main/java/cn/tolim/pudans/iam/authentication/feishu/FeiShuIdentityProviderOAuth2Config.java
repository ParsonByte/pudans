package cn.tolim.pudans.iam.authentication.feishu;

import cn.tolim.pudans.iam.authentication.core.client.IdentityProviderConfig;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;


@Data
@EqualsAndHashCode(callSuper = true)
public class FeiShuIdentityProviderOAuth2Config extends IdentityProviderConfig {
    @Serial
    private static final long serialVersionUID = -6850223527422243076L;

    // APP ID
    @NotBlank(message = "APP ID 不能为空")
    private String appId;

    // APP Secret
    @NotBlank(message = "APP Secret 不能为空")
    private String appSecret;
}
