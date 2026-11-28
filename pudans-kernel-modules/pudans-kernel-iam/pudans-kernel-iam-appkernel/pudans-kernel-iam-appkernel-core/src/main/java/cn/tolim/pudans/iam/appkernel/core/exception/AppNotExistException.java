package cn.tolim.pudans.iam.appkernel.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppNotExistException extends PudansException {
    public AppNotExistException() {
        super("app_not_exist", "应用不存在", DEFAULT_STATUS);
    }
}
