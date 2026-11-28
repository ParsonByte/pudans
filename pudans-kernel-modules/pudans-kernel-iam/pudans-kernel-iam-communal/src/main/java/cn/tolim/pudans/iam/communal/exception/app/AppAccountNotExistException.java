package cn.tolim.pudans.iam.communal.exception.app;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppAccountNotExistException extends PudansException {

    public AppAccountNotExistException() {
        super("app_account_not_exist", "应用账户不存在", DEFAULT_STATUS);
    }
}
