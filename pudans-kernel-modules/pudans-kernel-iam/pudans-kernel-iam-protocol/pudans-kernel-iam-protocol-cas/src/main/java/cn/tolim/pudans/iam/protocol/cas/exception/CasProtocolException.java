package cn.tolim.pudans.iam.protocol.cas.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class CasProtocolException extends PudansException {

    public CasProtocolException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    public CasProtocolException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }
}