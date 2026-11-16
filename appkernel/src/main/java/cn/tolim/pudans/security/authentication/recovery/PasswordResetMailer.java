package cn.tolim.pudans.security.authentication.recovery;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
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

    @PostConstruct
    void warnIfLogCodesEnabledOutsideDev() {
        var options = pudansProperties.getSecurity().getPasswordReset();
        if (options.isLogCodes() && !environment.matchesProfiles("dev", "test")) {
            log.warn(
                    "Password reset logCodes is enabled outside dev/test; verification codes may be "
                            + "written to the application log when email delivery is not configured.");
        }
    }

    public Mono<Void> sendCode(String email, String code) {
        var options = pudansProperties.getSecurity().getPasswordReset();
        var mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            if (!options.isLogCodes()) {
                return Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "recovery-mail-unavailable",
                        "Email delivery is not configured. Set spring.mail.* before using password recovery."));
            }
            log.warn(
                    "No JavaMailSender is configured. Password reset code for {} is {} (development log delivery).",
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
                                "Could not send the verification email.",
                                error))
                .then();
    }
}
