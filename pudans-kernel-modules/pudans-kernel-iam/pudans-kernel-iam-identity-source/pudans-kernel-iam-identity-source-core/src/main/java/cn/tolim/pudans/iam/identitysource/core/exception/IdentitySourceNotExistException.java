package cn.tolim.pudans.iam.identitysource.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class IdentitySourceNotExistException extends PudansException {
    public IdentitySourceNotExistException() {
        super("identity_source_not_exist", "身份源不存在", DEFAULT_STATUS);
    }
}
