package cn.tolim.pudans.iam.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "绑定到待处理 MFA 挑战的 WebAuthn 断言")
public class IamMfaWebAuthnVerifyRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -2418697851733086241L;

    @Schema(description = "登录返回的 MFA 挑战ID")
    private String challengeId;

    @Schema(description = "挑战所属用户名")
    private String username;

    @Schema(description = "本次登录所属的应用编码")
    private String appCode;

    @Schema(description = "选项端点返回的 WebAuthn 挑战ID")
    private String webAuthnChallengeId;

    @Schema(description = "WebAuthn 凭证ID")
    private String credentialId;

    @Schema(description = "Base64URL 编码的客户端数据 JSON")
    private String clientDataJson;

    @Schema(description = "Base64URL 编码的认证器数据")
    private String authenticatorData;

    @Schema(description = "Base64URL 编码的断言签名")
    private String signature;

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

    public String getWebAuthnChallengeId() {
        return webAuthnChallengeId;
    }

    public void setWebAuthnChallengeId(String webAuthnChallengeId) {
        this.webAuthnChallengeId = webAuthnChallengeId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getClientDataJson() {
        return clientDataJson;
    }

    public void setClientDataJson(String clientDataJson) {
        this.clientDataJson = clientDataJson;
    }

    public String getAuthenticatorData() {
        return authenticatorData;
    }

    public void setAuthenticatorData(String authenticatorData) {
        this.authenticatorData = authenticatorData;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}