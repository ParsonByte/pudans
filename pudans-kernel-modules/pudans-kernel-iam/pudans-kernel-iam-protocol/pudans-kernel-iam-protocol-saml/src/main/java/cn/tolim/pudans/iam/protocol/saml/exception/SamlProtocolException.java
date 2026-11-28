package cn.tolim.pudans.iam.protocol.saml.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class SamlProtocolException extends PudansException {

    public SamlProtocolException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    public SamlProtocolException(String errorCode, String message, Throwable cause) {
        super(cause, errorCode, message, HttpStatus.BAD_REQUEST);
    }

    public SamlProtocolException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public SamlProtocolException(String errorCode, String message, HttpStatus status,
                                 Throwable cause) {
        super(cause, errorCode, message, status);
    }
}