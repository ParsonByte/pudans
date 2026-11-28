package cn.tolim.pudans.iam.appkernel.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppNotEnableException extends PudansException {
    public AppNotEnableException() {
        super("app_not_enable", "应用未启用", DEFAULT_STATUS);
    }
}
