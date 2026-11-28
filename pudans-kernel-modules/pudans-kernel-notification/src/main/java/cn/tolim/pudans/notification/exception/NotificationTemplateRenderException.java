package cn.tolim.pudans.notification.exception;

import org.springframework.http.HttpStatus;

// Raised when a FreeMarker template cannot be rendered with the supplied parameters.
public class NotificationTemplateRenderException extends NotificationException {

    public NotificationTemplateRenderException(String message, Throwable cause) {
        super("notification_template_render_failed", message, HttpStatus.UNPROCESSABLE_CONTENT,
                cause);
    }
}