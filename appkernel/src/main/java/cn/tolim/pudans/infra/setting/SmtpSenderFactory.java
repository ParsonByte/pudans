package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.api.ParameterSetting;
import org.springframework.mail.javamail.JavaMailSender;

public interface SmtpSenderFactory {

    JavaMailSender create(ParameterSetting.Mail mail);
}
