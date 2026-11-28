package cn.tolim.pudans.iam.communal.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;


public class UserNotFoundException extends PudansException {
    public UserNotFoundException() {
        super("user_not_found_error", "未找到用户异常", HttpStatus.UNAUTHORIZED);
    }
}
