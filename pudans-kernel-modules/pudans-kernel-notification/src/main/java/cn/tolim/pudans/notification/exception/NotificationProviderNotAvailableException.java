package cn.tolim.pudans.notification.exception;

import org.springframework.http.HttpStatus;

// Raised when no enabled provider can serve a notification channel.
public class NotificationProviderNotAvailableException extends NotificationException {

    public NotificationProviderNotAvailableException(String message) {
        super("notification_provider_not_available", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}