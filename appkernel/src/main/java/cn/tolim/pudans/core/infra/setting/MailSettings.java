package cn.tolim.pudans.core.infra.setting;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.infra.exception.ApiException;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailSettings {

    private static final String PASSWORD_RESET_TEMPLATE = "passwordReset";

    private static final String TEST_TEMPLATE = "test";

    private static final String DEFAULT_PASSWORD_RESET_TEXT = """
            Your PUDANS password reset verification code is:

            {{code}}

            The code expires in {{ttlMinutes}} minutes. If you did not request this, ignore this email.
            """;

    private final ParameterGroups parameterGroups;

    private final EmailTemplateRenderer templateRenderer;

    private final SmtpSenderFactory senderFactory;

    private final MailAttachmentLoader attachmentLoader;

    private final AtomicReference<CachedSender> cachedSender = new AtomicReference<>();

    public Mono<Boolean> isConfigured() {
        return parameterGroups
                .getMail()
                .map(mail -> StringUtils.hasText(mail.getSmtpHost()))
                .defaultIfEmpty(false);
    }

    public Mono<Void> sendPasswordResetCode(String email, String code, long ttlMinutes) {
        return parameterGroups.getSite().flatMap(site -> {
            var variables = new HashMap<String, String>();
            variables.put("siteName", StringUtils.hasText(site.getSiteName()) ? site.getSiteName() : "PUDANS");
            variables.put("siteUrl", site.getSiteUrl() == null ? "" : site.getSiteUrl());
            variables.put("username", email);
            variables.put("actionLink", "");
            variables.put("code", code);
            variables.put("ttlMinutes", String.valueOf(ttlMinutes));
            return send(
                    email,
                    "PUDANS password reset verification code",
                    PASSWORD_RESET_TEMPLATE,
                    DEFAULT_PASSWORD_RESET_TEXT,
                    variables);
        });
    }

    public Mono<Void> sendTest(String to) {
        return parameterGroups.getSite().flatMap(site -> {
            var variables = new HashMap<String, String>();
            variables.put("siteName", StringUtils.hasText(site.getSiteName()) ? site.getSiteName() : "PUDANS");
            variables.put("siteUrl", site.getSiteUrl() == null ? "" : site.getSiteUrl());
            return send(
                    to,
                    "PUDANS test email",
                    TEST_TEMPLATE,
                    "This is a test email from {{siteName}}. SMTP settings are working.",
                    variables);
        });
    }

    public Mono<Void> send(
            String to,
            String subject,
            String templateKey,
            String defaultText,
            Map<String, ?> variables) {
        return parameterGroups.getMail().flatMap(mail -> {
            if (!StringUtils.hasText(mail.getSmtpHost())) {
                return Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "mail-not-configured",
                        "SMTP 尚未配置。请先设置邮件参数。"));
            }
            var template = mail.getMailTemplates() == null
                    ? null
                    : mail.getMailTemplates().get(templateKey);
            var text = templateRenderer.render(
                    StringUtils.hasText(template) ? template : defaultText, variables);
            return Mono.fromRunnable(() -> sendNow(mail, to, subject, templateKey, text))
                    .subscribeOn(Schedulers.boundedElastic());
        })
                .onErrorMap(MailException.class, error -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "mail-send-failed",
                        "无法发送邮件。",
                        error))
                .then();
    }

    private void sendNow(ParameterSetting.Mail mail, String to, String subject, String templateKey, String text) {
        var sender = sender(mail);
        var attachments = attachmentLoader.load(mail, templateKey);
        if (attachments.isEmpty()) {
            var message = new SimpleMailMessage();
            var from = from(mail);
            if (StringUtils.hasText(from)) {
                message.setFrom(from);
            }
            message.setTo(to);
            if (StringUtils.hasText(mail.getMailReplyTo())) {
                message.setReplyTo(mail.getMailReplyTo());
            }
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
        } else {
            sendWithAttachments(sender, mail, to, subject, text, attachments);
        }
        log.info("邮件 [{}] 已通过配置的 SMTP 服务器发送", subject);
    }

    private static void sendWithAttachments(
            JavaMailSender sender,
            ParameterSetting.Mail mail,
            String to,
            String subject,
            String text,
            java.util.List<MailAttachmentLoader.MailAttachment> attachments) {
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            var from = from(mail);
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(to);
            if (StringUtils.hasText(mail.getMailReplyTo())) {
                helper.setReplyTo(mail.getMailReplyTo());
            }
            helper.setSubject(subject);
            helper.setText(text, false);
            for (var attachment : attachments) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()));
            }
            sender.send(message);
        } catch (MessagingException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "mail-prepare-failed", "无法准备邮件。", e);
        }
    }

    private JavaMailSender sender(ParameterSetting.Mail mail) {
        var key = senderKey(mail);
        var cached = cachedSender.get();
        if (cached != null && cached.key().equals(key)) {
            return cached.sender();
        }
        var sender = senderFactory.create(mail);
        cachedSender.set(new CachedSender(key, sender));
        return sender;
    }

    private static String senderKey(ParameterSetting.Mail mail) {
        return String.join(
                "|",
                Objects.toString(mail.getSmtpHost(), ""),
                Objects.toString(mail.getSmtpPort(), ""),
                Objects.toString(mail.getSmtpUsername(), ""),
                Objects.toString(mail.getSmtpPassword(), ""),
                Objects.toString(mail.getSmtpForceSsl(), ""),
                Objects.toString(mail.getSmtpConnectionTtlSeconds(), ""));
    }

    private static String from(ParameterSetting.Mail mail) {
        if (!StringUtils.hasText(mail.getMailFromAddress())) {
            return null;
        }
        return StringUtils.hasText(mail.getMailFromName())
                ? mail.getMailFromName() + " <" + mail.getMailFromAddress() + ">"
                : mail.getMailFromAddress();
    }

    private record CachedSender(String key, JavaMailSender sender) {}
}
