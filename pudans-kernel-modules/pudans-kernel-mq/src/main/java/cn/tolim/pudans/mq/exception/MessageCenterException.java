package cn.tolim.pudans.mq.exception;

import cn.tolim.pudans.communal.core.error.ApiException;

public class MessageCenterException extends ApiException {

    public MessageCenterException(String code, int status, String message) {
        super(code, status, message);
    }

    public static MessageCenterException badRequest(String message) {
        return new MessageCenterException("MQ_BAD_REQUEST", 400, message);
    }

    public static MessageCenterException notFound(String subject, String id) {
        return new MessageCenterException("MQ_NOT_FOUND", 404, subject + " not found: " + id);
    }

    public static MessageCenterException conflict(String message) {
        return new MessageCenterException("MQ_CONFLICT", 409, message);
    }

    public static MessageCenterException internal(String message) {
        return new MessageCenterException("MQ_INTERNAL_ERROR", 500, message);
    }
}