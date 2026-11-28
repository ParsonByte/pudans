package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

// Sends email through a configured SMTP server.
@Component
public class EmailNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public Set<ProviderType> providerTypes() {
        return Set.of(ProviderType.SMTP);
    }

    @Override
    public SendResult send(NotificationMessage message, NotificationProviderConfig config) {
        try {
            JavaMailSenderImpl mailSender = buildMailSender(config);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            String from = config.require(ProviderConfigKeys.FROM);
            String fromName = config.get(ProviderConfigKeys.FROM_NAME);
            if (fromName == null) {
                helper.setFrom(from);
            } else {
                helper.setFrom(from, fromName);
            }
            helper.setTo(recipients(message.getRecipient()));
            if (message.getSubject() != null) {
                helper.setSubject(message.getSubject());
            }
            String content = message.getContent() == null ? "" : message.getContent();
            helper.setText(content, config.getBoolean(ProviderConfigKeys.HTML, true));
            mailSender.send(mimeMessage);
            return SendResult.success(null, "smtp accepted");
        } catch (Exception exception) {
            return SendResult.failure(exception.getMessage());
        }
    }

    private JavaMailSenderImpl buildMailSender(NotificationProviderConfig config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.require(ProviderConfigKeys.HOST));
        boolean ssl = config.getBoolean(ProviderConfigKeys.SSL_ENABLED, false);
        mailSender.setPort(config.getInt(ProviderConfigKeys.PORT, ssl ? 465 : 25));
        String username = config.get(ProviderConfigKeys.USERNAME);
        String password = config.get(ProviderConfigKeys.PASSWORD);
        if (username != null) {
            mailSender.setUsername(username);
        }
        if (password != null) {
            mailSender.setPassword(password);
        }
        mailSender.setDefaultEncoding("UTF-8");
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", String.valueOf(username != null));
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
        if (ssl) {
            properties.setProperty("mail.smtp.ssl.enable", "true");
        }
        if (config.getBoolean(ProviderConfigKeys.STARTTLS_ENABLED, false)) {
            properties.setProperty("mail.smtp.starttls.enable", "true");
        }
        mailSender.setJavaMailProperties(properties);
        return mailSender;
    }

    private String[] recipients(String recipient) {
        return Arrays.stream(recipient.split("[,;]"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }
}