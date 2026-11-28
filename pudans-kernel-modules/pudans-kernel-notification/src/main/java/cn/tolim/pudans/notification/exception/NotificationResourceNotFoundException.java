package cn.tolim.pudans.notification.exception;

import org.springframework.http.HttpStatus;

// Raised when a template, provider, send record or dead letter does not exist.
public class NotificationResourceNotFoundException extends NotificationException {

    public NotificationResourceNotFoundException(String message) {
        super("notification_resource_not_found", message, HttpStatus.NOT_FOUND);
    }
}