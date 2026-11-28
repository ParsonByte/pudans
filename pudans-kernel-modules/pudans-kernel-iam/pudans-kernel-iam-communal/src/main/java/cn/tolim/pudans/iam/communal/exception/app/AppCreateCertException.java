package cn.tolim.pudans.iam.communal.exception.app;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppCreateCertException extends PudansException {

    public AppCreateCertException() {
        super("app_create_cert_error", "创建应用证书失败", DEFAULT_STATUS);
    }
}
