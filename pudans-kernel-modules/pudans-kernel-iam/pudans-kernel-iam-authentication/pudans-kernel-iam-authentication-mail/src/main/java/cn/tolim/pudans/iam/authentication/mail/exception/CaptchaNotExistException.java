package cn.tolim.pudans.iam.authentication.mail.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class CaptchaNotExistException extends PudansException {
    public CaptchaNotExistException() {
        super("captcha_not_exist", "验证码不存在", DEFAULT_STATUS);
    }
}
