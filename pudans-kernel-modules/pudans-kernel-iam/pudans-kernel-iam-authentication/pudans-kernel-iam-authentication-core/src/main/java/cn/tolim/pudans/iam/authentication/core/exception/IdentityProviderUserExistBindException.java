package cn.tolim.pudans.iam.authentication.core.exception;

import org.springframework.http.HttpStatus;


public class IdentityProviderUserExistBindException extends UserBindIdentityProviderException {
    public IdentityProviderUserExistBindException() {
        super("idp_user_exist_bind", "IDP用户存在绑定", HttpStatus.BAD_REQUEST);
    }
}
