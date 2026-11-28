package cn.tolim.pudans.iam.appkernel.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppNotConfigException extends PudansException {
    public AppNotConfigException() {
        super("app_not_config", "应用未配置", DEFAULT_STATUS);
    }
}
