package cn.tolim.pudans.iam.authentication.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;


public class IdentityProviderNotExistException extends PudansException {

    public IdentityProviderNotExistException() {
        super("idp_not_exist", "身份提供商不存在", BAD_REQUEST);
    }
}
