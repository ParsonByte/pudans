package cn.tolim.pudans.notification.exception;

import org.springframework.http.HttpStatus;

// Raised when a notification request or provider configuration is invalid.
public class NotificationValidationException extends NotificationException {

    public NotificationValidationException(String message) {
        super("notification_validation_failed", message, HttpStatus.BAD_REQUEST);
    }
}