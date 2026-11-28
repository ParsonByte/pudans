package cn.tolim.pudans.iam.core.auth.audit;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

public class AuthAuditEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -2267167156645752197L;

    private final AuthAuditType type;
    private final String userId;
    private final String userType;
    private final String appCode;
    private final String sid;
    private final String ip;
    private final String userAgent;
    private final String result;
    private final String reason;
    private final LocalDateTime occurredAt;

    public AuthAuditEvent(AuthAuditType type, String userId, String userType, String appCode,
                          String sid, String ip, String userAgent, String result, String reason) {
        this.type = type;
        this.userId = userId;
        this.userType = userType;
        this.appCode = appCode;
        this.sid = sid;
        this.ip = ip;
        this.userAgent = userAgent;
        this.result = result;
        this.reason = reason;
        this.occurredAt = LocalDateTime.now();
    }

    public AuthAuditType getType() {
        return type;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserType() {
        return userType;
    }

    public String getAppCode() {
        return appCode;
    }

    public String getSid() {
        return sid;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}