package cn.tolim.pudans.iam.core.auth.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

public class AppAccessDeniedException extends PudansException {

    public AppAccessDeniedException(String appCode) {
        super("app_access_denied", "Access to application [" + appCode + "] is denied",
                HttpStatus.FORBIDDEN);
    }
}