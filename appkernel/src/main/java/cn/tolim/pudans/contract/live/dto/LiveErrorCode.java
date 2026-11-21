package cn.tolim.pudans.contract.live.dto;

import cn.tolim.pudans.infra.exception.ApiException;
import org.springframework.http.HttpStatus;

public enum LiveErrorCode {

    LIVE_ROOM_LINK_INVALID(HttpStatus.BAD_REQUEST, "直播间链接无效"),
    LIVE_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "直播间不存在"),
    LIVE_RESOLVE_FAILED(HttpStatus.BAD_GATEWAY, "直播间解析失败"),
    LIVE_ROOM_OFFLINE(HttpStatus.CONFLICT, "直播间未开播"),
    LIVE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "直播间解析被限流"),
    LIVE_SOURCE_DUPLICATE(HttpStatus.CONFLICT, "同一直播间已存在启用的直播源"),
    LIVE_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "直播源不存在"),
    LIVE_RECORDING_ACTIVE(HttpStatus.CONFLICT, "直播源存在进行中的录制"),
    LIVE_RECORDING_NOT_ACTIVE(HttpStatus.CONFLICT, "录制任务不在进行中"),
    LIVE_RECORDING_NOT_READY(HttpStatus.CONFLICT, "录制任务尚未完成"),
    LIVE_RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "录制任务不存在"),
    LIVE_RECORDING_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "录制并发已达上限"),
    LIVE_STREAM_FAILED(HttpStatus.BAD_GATEWAY, "直播流拉取失败"),
    LIVE_DISK_FULL(HttpStatus.INSUFFICIENT_STORAGE, "录制磁盘空间不足"),
    LIVE_STORAGE_POLICY_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "缺少可用的对象存储策略"),
    LIVE_STORAGE_POLICY_INVALID(HttpStatus.BAD_REQUEST, "存储策略不支持录制存储"),
    LIVE_STORAGE_BACKLOG(HttpStatus.INSUFFICIENT_STORAGE, "录制成品上传积压"),
    LIVE_MERGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "录制分片合并失败"),
    LIVE_STORAGE_FAILED(HttpStatus.BAD_GATEWAY, "录制文件存储失败"),
    LIVE_STOP_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "停止录制超时"),
    LIVE_RECORDER_LOST(HttpStatus.INTERNAL_SERVER_ERROR, "录制任务执行实例已失联"),
    LIVE_CLEANUP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "录制文件清理失败"),
    LIVE_NO_DATA(HttpStatus.CONFLICT, "录制任务没有可用的数据"),
    LIVE_INVALID_SPEC(HttpStatus.BAD_REQUEST, "直播源配置无效"),
    LIVE_INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "请求参数无效");

    private final HttpStatus status;

    private final String message;

    LiveErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public ApiException exception() {
        return new ApiException(status, name(), message);
    }
}
