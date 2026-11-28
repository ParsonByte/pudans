package cn.tolim.pudans.communal.core.error;

public enum CommonErrorCode {

    BAD_REQUEST(400, "错误请求"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源未找到"),
    RATE_LIMITED(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int status;
    private final String message;

    CommonErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public String code() {
        return name();
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }
}