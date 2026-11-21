package cn.tolim.pudans.security.authentication.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.MailSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PasswordResetLogCodesTest {

    @Mock
    ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    MailSettings mailSettings;

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(PasswordResetMailer.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static PudansProperties properties(boolean logCodes) {
        var properties = new PudansProperties();
        properties.getSecurity().getPasswordReset().setLogCodes(logCodes);
        return properties;
    }

    @Test
    void logCodesIsDisabledByDefault() {
        assertThat(new SecurityProperties().getPasswordReset().isLogCodes()).isFalse();
    }

    @Test
    void mailerFailsClosedWhenLogCodesAreDisabled() {
        var mailer = new PasswordResetMailer(
                mailSenderProvider, properties(false), new MockEnvironment(), mailSettings);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        when(mailSettings.isConfigured()).thenReturn(reactor.core.publisher.Mono.just(false));

        StepVerifier.create(mailer.sendCode("alice@example.com", "123456"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode().value()).isEqualTo(503);
                    assertThat(apiException.getCode()).isEqualTo("recovery-mail-unavailable");
                })
                .verify();

        assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains("123456"));
    }

    @Test
    void mailerLogsTheCodeOnlyWhenExplicitlyEnabled() {
        var mailer = new PasswordResetMailer(
                mailSenderProvider, properties(true), new MockEnvironment(), mailSettings);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        when(mailSettings.isConfigured()).thenReturn(reactor.core.publisher.Mono.just(false));

        StepVerifier.create(mailer.sendCode("alice@example.com", "654321"))
                .verifyComplete();

        assertThat(appender.list)
                .anyMatch(event -> event.getFormattedMessage().contains("654321"));
    }

    @Test
    void startupWarnsWhenLogCodesAreEnabledOutsideDev() {
        var mailer = new PasswordResetMailer(
                mailSenderProvider, properties(true), new MockEnvironment(), mailSettings);

        mailer.warnIfLogCodesEnabledOutsideDev();

        assertThat(appender.list)
                .anyMatch(event -> event.getFormattedMessage().contains("logCodes"));
    }

    @Test
    void startupDoesNotWarnInDevProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        var mailer = new PasswordResetMailer(mailSenderProvider, properties(true), environment, mailSettings);

        mailer.warnIfLogCodesEnabledOutsideDev();

        assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains("logCodes"));
    }
}
