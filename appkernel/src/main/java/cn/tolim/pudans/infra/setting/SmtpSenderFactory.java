package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import org.springframework.mail.javamail.JavaMailSender;

public interface SmtpSenderFactory {

    JavaMailSender create(ParameterSetting.Mail mail);
}
