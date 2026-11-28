package cn.tolim.pudans.iam.identitysource.feishu;

import cn.tolim.pudans.iam.communal.jackjson.encrypt.JsonPropertyEncrypt;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;


@Getter
@Setter
public class FeiShuConfig extends IdentitySourceConfig {

    @Serial
    private static final long serialVersionUID = -834514351515253275L;
    // Application App key
    @NotEmpty(message = "AppId不能为空")
    private String appId;

    // Application AppSecret
    @JsonPropertyEncrypt
    @NotEmpty(message = "AppSecret不能为空")
    private String appSecret;

    // encryptKey
    @JsonPropertyEncrypt
    private String encryptKey;

    // verificationToken
    @JsonPropertyEncrypt
    private String verificationToken;

    public FeiShuConfig() {
    }
}
