package cn.tolim.pudans.iam.communal.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class UserGroupExistUserException extends PudansException {

    public UserGroupExistUserException() {
        this("用户组存在用户");
    }

    public UserGroupExistUserException(String message) {
        super("user_group_exist_user", message, HttpStatus.BAD_REQUEST);
    }
}
