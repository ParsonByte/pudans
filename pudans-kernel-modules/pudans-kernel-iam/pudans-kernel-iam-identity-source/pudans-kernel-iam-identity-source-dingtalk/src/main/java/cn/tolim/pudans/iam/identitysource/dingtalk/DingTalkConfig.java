package cn.tolim.pudans.iam.identitysource.dingtalk;

import cn.tolim.pudans.iam.communal.jackjson.encrypt.JsonPropertyEncrypt;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;


@Getter
@Setter
public class DingTalkConfig extends IdentitySourceConfig {

    @Serial
    private static final long serialVersionUID = 8537679521873685897L;
    // Enterprise ID
    private String corpId;
    // Application App key
    @NotEmpty(message = "AppKey不能为空")
    private String appKey;

    // Application AppSecret
    @JsonPropertyEncrypt
    @NotEmpty(message = "AppSecret不能为空")
    private String appSecret;

    // Encryption AES key. Required for real-time sync.
    @JsonPropertyEncrypt
    private String aesKey;

    // Signature token
    @JsonPropertyEncrypt
    private String token;

    public DingTalkConfig() {
    }
}
