package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import org.springframework.mail.javamail.JavaMailSender;

public interface SmtpSenderFactory {

    // 根据邮件设置创建邮件发送器。
    JavaMailSender create(ParameterSetting.Mail mail);
}
