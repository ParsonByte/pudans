package cn.tolim.pudans.core.ai.runtime.sandbox;

import cn.tolim.pudans.core.infra.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class SandboxErrors {

    public static final String PROVIDER_MISSING = "ai-runtime-provider-missing";

    public static final String CONFIG_INVALID = "ai-runtime-config-invalid";

    public static final String AGENT_NOT_CONFIGURED = "ai-runtime-agent-not-configured";

    public static final String NOT_FOUND = "ai-runtime-not-found";

    public static final String NOT_RUNNING = "ai-runtime-not-running";

    public static final String LOCKED = "ai-runtime-locked";

    public static final String LOCK_FAILED = "ai-runtime-lock-failed";

    public static final String CREATE_FAILED = "ai-runtime-create-failed";

    public static final String DESTROY_FAILED = "ai-runtime-destroy-failed";

    public static final String EXEC_FAILED = "ai-runtime-exec-failed";

    public static final String FILE_FAILED = "ai-runtime-file-failed";

    public static final String FILE_TOO_LARGE = "ai-runtime-file-too-large";

    public static final String LOG_FAILED = "ai-runtime-log-failed";

    public static final String PROVIDER_ERROR = "ai-runtime-provider-error";

    public static final String PROVIDER_RESPONSE = "ai-runtime-provider-response";

    private SandboxErrors() {}

    public static ApiException providerMissing(String provider) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, PROVIDER_MISSING, "未配置提供方 " + provider + " 的可用沙箱提供方资源。");
    }

    public static ApiException configInvalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CONFIG_INVALID, message);
    }

    public static ApiException agentNotConfigured(String agentName) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, AGENT_NOT_CONFIGURED, "Agent " + agentName + " 未配置沙箱运行时。");
    }

    public static ApiException notFound(String agentName) {
        return new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, "Agent " + agentName + " 的运行时不存在。");
    }

    public static ApiException notRunning(String agentName) {
        return new ApiException(
                HttpStatus.CONFLICT, NOT_RUNNING, "Agent " + agentName + " 的沙箱未处于运行状态。");
    }

    public static ApiException locked(String agentName) {
        return new ApiException(HttpStatus.CONFLICT, LOCKED, "Agent " + agentName + " 的运行时正在被其他操作占用。");
    }

    public static ApiException lockFailed(String agentName, Throwable cause) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, LOCK_FAILED, "无法获取 Agent " + agentName + " 的运行时锁。", cause);
    }

    public static ApiException createFailed(String agentName, Throwable cause) {
        if (cause instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, CREATE_FAILED, "Agent " + agentName + " 的沙箱创建失败。", cause);
    }

    public static ApiException destroyFailed(String agentName, Throwable cause) {
        if (cause instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, DESTROY_FAILED, "Agent " + agentName + " 的沙箱销毁失败。", cause);
    }

    public static ApiException execFailed(String agentName, Throwable cause) {
        if (cause instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, EXEC_FAILED, "Agent " + agentName + " 的命令执行失败。", cause);
    }

    public static ApiException fileFailed(String agentName, Throwable cause) {
        if (cause instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, FILE_FAILED, "Agent " + agentName + " 的文件操作失败。", cause);
    }

    public static ApiException fileTooLarge(long size, long limit) {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                FILE_TOO_LARGE,
                "文件大小 " + size + " 字节超过上限 " + limit + " 字节。");
    }

    public static ApiException logFailed(String agentName, Throwable cause) {
        if (cause instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, LOG_FAILED, "Agent " + agentName + " 的日志读取失败。", cause);
    }

    public static ApiException providerFailure(String provider, String action, Throwable cause) {
        if (cause instanceof ApiException apiException) {
            return apiException;
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, PROVIDER_ERROR, provider + " 沙箱" + action + "失败。", cause);
    }

    public static ApiException providerResponse(String provider, String action, Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY, PROVIDER_RESPONSE, provider + " 沙箱" + action + "响应无法解析。", cause);
    }

    public static ApiException providerRejected(
            String provider, String action, String code, String message) {
        var hasCode = code != null && !code.isBlank();
        var hasMessage = message != null && !message.isBlank();
        var detail = hasCode && hasMessage ? code + ": " + message : hasCode ? code : message;
        if (detail == null || detail.isBlank()) {
            detail = "未知错误";
        }
        if (detail.length() > 300) {
            detail = detail.substring(0, 300);
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, PROVIDER_ERROR, provider + " 沙箱" + action + "被拒绝：" + detail);
    }
}