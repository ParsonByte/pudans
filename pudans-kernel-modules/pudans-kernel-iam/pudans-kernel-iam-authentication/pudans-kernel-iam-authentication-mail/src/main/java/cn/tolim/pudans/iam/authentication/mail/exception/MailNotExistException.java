package cn.tolim.pudans.iam.authentication.mail.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class MailNotExistException extends PudansException {
    public MailNotExistException() {
        super("mail_not_exist", "邮箱不存在", DEFAULT_STATUS);
    }

}
