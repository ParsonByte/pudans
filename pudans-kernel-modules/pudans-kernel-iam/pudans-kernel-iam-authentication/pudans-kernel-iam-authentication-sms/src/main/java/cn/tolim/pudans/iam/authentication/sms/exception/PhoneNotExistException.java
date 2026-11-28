package cn.tolim.pudans.iam.authentication.sms.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class PhoneNotExistException extends PudansException {
    public PhoneNotExistException() {
        super("phone_not_exist", "手机号不存在", DEFAULT_STATUS);
    }

}
