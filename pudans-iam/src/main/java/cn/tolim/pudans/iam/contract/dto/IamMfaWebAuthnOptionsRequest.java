package cn.tolim.pudans.iam.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "针对待处理 MFA 挑战的 WebAuthn 认证选项请求")
public class IamMfaWebAuthnOptionsRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6728453172214094183L;

    @Schema(description = "登录返回的 MFA 挑战ID")
    private String challengeId;

    @Schema(description = "挑战所属用户名")
    private String username;

    @Schema(description = "本次登录所属的应用编码")
    private String appCode;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }
}