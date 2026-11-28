package cn.tolim.pudans.iam.authentication.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;


public class IdentityProviderTemplateNotExistException extends PudansException {

    public IdentityProviderTemplateNotExistException() {
        super("idp_template_not_exist", "身份提供商模版不存在", BAD_REQUEST);
    }
}
