package cn.tolim.pudans.iam.communal.exception.app;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class AppAccessDeniedException extends PudansException {

    public AppAccessDeniedException() {
        super("app_access_denied", "应用访问权限不足", HttpStatus.FORBIDDEN);
    }
}
