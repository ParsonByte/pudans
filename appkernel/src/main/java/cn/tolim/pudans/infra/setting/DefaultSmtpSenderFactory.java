package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.api.ParameterSetting;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultSmtpSenderFactory implements SmtpSenderFactory {

    private static final int DEFAULT_SMTP_PORT = 587;

    private static final int SOCKET_TIMEOUT_MILLIS = 5000;

    @Override
    public JavaMailSender create(ParameterSetting.Mail mail) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(mail.getSmtpHost());
        sender.setPort(mail.getSmtpPort() == null ? DEFAULT_SMTP_PORT : mail.getSmtpPort());
        if (StringUtils.hasText(mail.getSmtpUsername())) {
            sender.setUsername(mail.getSmtpUsername());
        }
        if (StringUtils.hasText(mail.getSmtpPassword())) {
            sender.setPassword(mail.getSmtpPassword());
        }
        sender.setDefaultEncoding("UTF-8");
        var properties = sender.getJavaMailProperties();
        var authenticated = StringUtils.hasText(mail.getSmtpUsername());
        var forceSsl = Boolean.TRUE.equals(mail.getSmtpForceSsl());
        var connectionTtlMillis = (mail.getSmtpConnectionTtlSeconds() == null
                        ? 300
                        : mail.getSmtpConnectionTtlSeconds())
                * 1000L;
        properties.put("mail.smtp.auth", String.valueOf(authenticated));
        properties.put("mail.smtp.ssl.enable", String.valueOf(forceSsl));
        properties.put("mail.smtp.starttls.enable", String.valueOf(!forceSsl));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(SOCKET_TIMEOUT_MILLIS));
        properties.put("mail.smtp.timeout", String.valueOf(SOCKET_TIMEOUT_MILLIS));
        properties.put("mail.smtp.writetimeout", String.valueOf(SOCKET_TIMEOUT_MILLIS));
        properties.put("mail.smtp.connectionpool", "true");
        properties.put("mail.smtp.connectionpooltimeout", String.valueOf(connectionTtlMillis));
        return sender;
    }
}
