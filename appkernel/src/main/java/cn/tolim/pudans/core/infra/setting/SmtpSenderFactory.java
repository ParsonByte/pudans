package cn.tolim.pudans.core.infra.setting;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import org.springframework.mail.javamail.JavaMailSender;

public interface SmtpSenderFactory {

    JavaMailSender create(ParameterSetting.Mail mail);
}
