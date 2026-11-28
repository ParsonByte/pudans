package cn.tolim.pudans.iam.identitysource.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class InvalidClientConfigException extends PudansException {
    public InvalidClientConfigException(String msg, Throwable t) {
        super(msg, t);
    }

    public InvalidClientConfigException(String msg) {
        super(msg);
    }
}
