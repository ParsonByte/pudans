package cn.tolim.pudans.iam.communal.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class MailMessageSendException extends PudansException {

    public MailMessageSendException() {
        super("message_send_error", "发送邮件消息失败", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public MailMessageSendException(Throwable cause) {
        super(cause, "message_send_error", "发送邮件消息失败", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Constructs a new runtime exception with the specified detail message.
    // The cause is not initialized, and may subsequently be initialized by a
    // call to initCause.
    // Parameter message: the detail message. The detail message is saved for
    // later retrieval by the getMessage() method.
    public MailMessageSendException(String message) {
        super(message);
    }

    // Constructs a new runtime exception with the specified detail message and
    // cause. Note that the detail message associated with
    // cause is not automatically incorporated in
    // this runtime exception's detail message.
    // Parameter message: the detail message (which is saved for later retrieval
    // by the getMessage() method).
    // Parameter cause: the cause (which is saved for later retrieval by the
    // getCause() method). (A null value is
    // permitted, and indicates that the cause is nonexistent or
    // unknown.)
    // Since: 1.4
    public MailMessageSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
