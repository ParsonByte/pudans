package cn.tolim.pudans.communal.core.contract;

public record ApiResponse<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "OK";
    public static final String SUCCESS_MESSAGE = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
