package cn.tolim.pudans.iam.identitysource.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class IdentitySourceNotConfiguredException extends PudansException {
    public IdentitySourceNotConfiguredException() {
        super("identity_source_not_configured", "身份源未配置", HttpStatus.BAD_REQUEST);
    }
}
