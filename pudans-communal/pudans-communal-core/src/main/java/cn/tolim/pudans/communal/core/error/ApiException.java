package cn.tolim.pudans.communal.core.error;

public class ApiException extends RuntimeException {

    private final String code;
    private final int status;

    public ApiException(CommonErrorCode errorCode) {
        this(errorCode.code(), errorCode.status(), errorCode.message());
    }

    public ApiException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}