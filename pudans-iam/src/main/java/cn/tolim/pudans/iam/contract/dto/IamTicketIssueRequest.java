package cn.tolim.pudans.iam.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "SSO 票据签发请求")
public class IamTicketIssueRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -4429768200387618722L;

    @Schema(description = "全局登录会话ID")
    private String sid;

    @Schema(description = "应用编码")
    private String appCode;

    @Schema(description = "返回的目标地址")
    private String targetUrl;

    @Schema(description = "targetUrl 的别名")
    private String target;

    @Schema(description = "绑定到票据的可选 nonce")
    private String nonce;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    // Resolves the target url from either field name
    // Returns: String
    public String resolvedTargetUrl() {
        if (targetUrl != null && !targetUrl.isBlank()) {
            return targetUrl;
        }
        return target;
    }
}