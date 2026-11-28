package cn.tolim.pudans.iam.communal.exception.app;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppDefaultAccountExistException extends PudansException {

    public AppDefaultAccountExistException() {
        super("app_default_account_exist", "默认应用账户已存在", DEFAULT_STATUS);
    }
}
