package cn.tolim.pudans.iam.core.auth.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

public class InvalidAuthRequestException extends PudansException {

    public InvalidAuthRequestException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}