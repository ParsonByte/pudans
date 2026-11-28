package cn.tolim.pudans.iam.authentication.core.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class UserBindIdentityProviderException extends PudansException {
    public static final String USER_BIND_IDP_ERROR = "user_bind_idp_error";

    public UserBindIdentityProviderException() {
        super(USER_BIND_IDP_ERROR, "用户绑定身份提供商错误", HttpStatus.FORBIDDEN);
    }

    public UserBindIdentityProviderException(String description, HttpStatus status) {
        super(USER_BIND_IDP_ERROR, description, status);
    }

    public UserBindIdentityProviderException(String error, String description, HttpStatus status) {
        super(error, description, status);
    }
}
