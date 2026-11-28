package cn.tolim.pudans.iam.appkernel.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class AppCertNotExistException extends PudansException {
    public AppCertNotExistException() {
        super("应用证书不存在");
    }
}
