package cn.tolim.pudans.iam.appkernel.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppConfigNotExistException extends PudansException {
    public AppConfigNotExistException() {
        super("app_config_not_exist", "应用配置不存在", DEFAULT_STATUS);
    }
}
