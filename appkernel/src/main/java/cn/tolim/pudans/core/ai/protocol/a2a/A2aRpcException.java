package cn.tolim.pudans.core.ai.protocol.a2a;

import org.jspecify.annotations.Nullable;

// JSON-RPC 错误码使用 A2A 规范保留区间，调用方据此区分协议错误与任务错误。
public final class A2aRpcException extends RuntimeException {

    public static final int PARSE_ERROR = -32700;

    public static final int INVALID_REQUEST = -32600;

    public static final int METHOD_NOT_FOUND = -32601;

    public static final int INVALID_PARAMS = -32602;

    public static final int INTERNAL_ERROR = -32603;

    public static final int TASK_NOT_FOUND = -32001;

    public static final int TASK_NOT_CANCELABLE = -32002;

    private final int code;

    private final @Nullable Object id;

    private A2aRpcException(int code, String message, @Nullable Object id) {
        super(message);
        this.code = code;
        this.id = id;
    }

    public static A2aRpcException parseError(String message) {
        return new A2aRpcException(PARSE_ERROR, message, null);
    }

    public static A2aRpcException invalidRequest(String message, @Nullable Object id) {
        return new A2aRpcException(INVALID_REQUEST, message, id);
    }

    public static A2aRpcException invalidParams(String message) {
        return new A2aRpcException(INVALID_PARAMS, message, null);
    }

    public static A2aRpcException taskNotFound(String taskId) {
        return new A2aRpcException(TASK_NOT_FOUND, "任务不存在：" + taskId, null);
    }

    public static A2aRpcException taskNotCancelable(String taskId) {
        return new A2aRpcException(TASK_NOT_CANCELABLE, "任务已结束，无法取消：" + taskId, null);
    }

    public int code() {
        return code;
    }

    public @Nullable Object id() {
        return id;
    }
}