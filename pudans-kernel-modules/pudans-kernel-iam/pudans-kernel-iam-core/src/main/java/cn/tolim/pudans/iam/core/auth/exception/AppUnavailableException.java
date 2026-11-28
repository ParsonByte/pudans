package cn.tolim.pudans.iam.core.auth.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

public class AppUnavailableException extends PudansException {

    public AppUnavailableException(String appCode) {
        super("app_unavailable", "Application [" + appCode + "] is unavailable", HttpStatus.BAD_REQUEST);
    }

    public AppUnavailableException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}