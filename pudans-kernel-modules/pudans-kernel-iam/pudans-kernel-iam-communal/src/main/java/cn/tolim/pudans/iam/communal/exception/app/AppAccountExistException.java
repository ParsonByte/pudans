package cn.tolim.pudans.iam.communal.exception.app;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppAccountExistException extends PudansException {

    public AppAccountExistException() {
        super("app_account_exist", "应用账户已存在", DEFAULT_STATUS);
    }
}
