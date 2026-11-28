package cn.tolim.pudans.iam.communal.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class UserGroupNotExistException extends PudansException {

    public UserGroupNotExistException() {
        this("用户组不存在");
    }

    public UserGroupNotExistException(String message) {
        super("user_group_not_exist", message, HttpStatus.BAD_REQUEST);
    }
}
