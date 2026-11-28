package cn.tolim.pudans.iam.communal.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

import java.io.Serial;


public class MailTemplateException extends PudansException {
    @Serial
    private static final long serialVersionUID = -6497956209061617684L;

    public MailTemplateException(String msg, Throwable t) {
        super(msg, t);
    }

    public MailTemplateException(String msg) {
        super(msg);
    }

    public MailTemplateException(String msg, HttpStatus status) {
        super(msg, status);
    }

    public MailTemplateException(String error, String description, HttpStatus status) {
        super(error, description, status);
    }

    public MailTemplateException(Throwable cause, String error, String description,
                                 HttpStatus status) {
        super(cause, error, description, status);
    }
}
