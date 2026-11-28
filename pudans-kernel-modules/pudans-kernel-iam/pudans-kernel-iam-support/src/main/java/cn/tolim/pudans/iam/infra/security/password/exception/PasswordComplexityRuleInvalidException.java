package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordComplexityRuleInvalidException extends PasswordInvalidException {

    public PasswordComplexityRuleInvalidException(String message) {
        super(message);
    }

    public PasswordComplexityRuleInvalidException(String message, HttpStatus status) {
        super(message, status);
    }

    public PasswordComplexityRuleInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasswordComplexityRuleInvalidException(String errorCode, String message,
                                                  HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordComplexityRuleInvalidException(Throwable cause, String errorCode,
                                                  String message, HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
