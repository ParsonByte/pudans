package cn.tolim.pudans.security.authentication.recovery;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.MailSettings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final PudansProperties pudansProperties;

    private final Environment environment;

    private final MailSettings mailSettings;

    @PostConstruct
    void warnIfLogCodesEnabledOutsideDev() {
        var options = pudansProperties.getSecurity().getPasswordReset();
        if (options.isLogCodes() && !environment.matchesProfiles("dev", "test")) {
            log.warn(
                    "密码重置的 logCodes 在非开发/测试环境已启用；未配置邮件投递时，验证码可能会写入应用日志。");
        }
    }

    public Mono<Void> sendCode(String email, String code) {
        var options = pudansProperties.getSecurity().getPasswordReset();
        return mailSettings.isConfigured().flatMap(configured -> {
            if (Boolean.TRUE.equals(configured)) {
                return mailSettings.sendPasswordResetCode(
                        email, code, options.getCodeTtl().toMinutes());
            }
            return sendWithFallbackSender(email, code, options);
        });
    }

    private Mono<Void> sendWithFallbackSender(
            String email, String code, SecurityProperties.PasswordResetOptions options) {
        var mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            if (!options.isLogCodes()) {
                return Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "recovery-mail-unavailable",
                        "未配置邮件投递。使用密码找回功能前，请先配置 spring.mail.*。"));
            }
            log.warn(
                    "未配置 JavaMailSender。{} 的密码重置验证码为 {}（开发日志投递）。",
                    email,
                    code);
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
                    var message = new SimpleMailMessage();
                    if (StringUtils.hasText(options.getFrom())) {
                        message.setFrom(options.getFrom());
                    }
                    message.setTo(email);
                    message.setSubject("PUDANS password reset verification code");
                    message.setText("""
                            Your PUDANS password reset verification code is:

                            %s

                            The code expires in %d minutes. If you did not request this, ignore this email.
                            """.formatted(code, options.getCodeTtl().toMinutes()));
                    mailSender.send(message);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        MailException.class,
                        error -> new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "recovery-mail-unavailable",
                                "无法发送验证邮件。",
                                error))
                .then();
    }
}
