package cn.tolim.pudans.notification.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

// Base exception for notification module failures.
public class NotificationException extends PudansException {

    public NotificationException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public NotificationException(String errorCode, String message, HttpStatus status,
                                 Throwable cause) {
        super(cause, errorCode, message, status);
    }
}