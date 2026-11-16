package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.infra.exception.ApiException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MailSettingsServiceTest {

    @Mock
    ParameterSettingGroupService parameterSettingGroupService;

    @Mock
    SmtpSenderFactory senderFactory;

    @Mock
    JavaMailSender mailSender;

    private MailSettingsService mailSettingsService;

    @BeforeEach
    void setUp() {
        mailSettingsService = new MailSettingsService(
                parameterSettingGroupService, new EmailTemplateRenderer(), senderFactory, new MailAttachmentLoader());
    }

    @Test
    void sendsAPasswordResetCodeThroughTheConfiguredSmtpServer() {
        when(parameterSettingGroupService.getMail()).thenReturn(Mono.just(mail()));
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site()));
        when(senderFactory.create(any(ParameterSetting.Mail.class))).thenReturn(mailSender);

        StepVerifier.create(mailSettingsService.sendPasswordResetCode("alice@example.com", "123456", 10))
                .verifyComplete();

        var message = capturedMessage();
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getFrom()).isEqualTo("PUDANS <no-reply@example.com>");
        assertThat(message.getText()).contains("123456");
    }

    @Test
    void usesTheConfiguredTemplateWhenPresent() {
        var mail = mail();
        mail.setMailTemplates(java.util.Map.of("passwordReset", "Hello {{username}}, code {{code}}."));
        when(parameterSettingGroupService.getMail()).thenReturn(Mono.just(mail));
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site()));
        when(senderFactory.create(any(ParameterSetting.Mail.class))).thenReturn(mailSender);

        StepVerifier.create(mailSettingsService.sendPasswordResetCode("alice@example.com", "654321", 10))
                .verifyComplete();

        assertThat(capturedMessage().getText()).isEqualTo("Hello alice@example.com, code 654321.");
    }

    @Test
    void failsWhenSmtpIsNotConfigured() {
        var mail = mail();
        mail.setSmtpHost("");
        when(parameterSettingGroupService.getMail()).thenReturn(Mono.just(mail));
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site()));

        StepVerifier.create(mailSettingsService.sendPasswordResetCode("alice@example.com", "123456", 10))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("mail-not-configured");
                })
                .verify();

        verify(senderFactory, never()).create(any());
    }

    @Test
    void mapsTransportFailuresToAnApiError() {
        when(parameterSettingGroupService.getMail()).thenReturn(Mono.just(mail()));
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site()));
        when(senderFactory.create(any(ParameterSetting.Mail.class))).thenReturn(mailSender);
        org.mockito.Mockito.doThrow(new MailSendException("down"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        StepVerifier.create(mailSettingsService.sendPasswordResetCode("alice@example.com", "123456", 10))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("mail-send-failed");
                })
                .verify();
    }

    @Test
    void sendsDeclarativeClasspathAttachments() {
        var attachment = new ParameterSetting.MailTemplateAttachment();
        attachment.setFileName("notes.txt");
        attachment.setClasspath("mail/attachment.txt");
        var mail = mail();
        mail.setMailTemplateAttachments(Map.of("test", List.of(attachment)));
        when(parameterSettingGroupService.getMail()).thenReturn(Mono.just(mail));
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site()));
        when(senderFactory.create(any(ParameterSetting.Mail.class))).thenReturn(mailSender);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        StepVerifier.create(mailSettingsService.sendTest("admin@example.com")).verifyComplete();

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testMailCanBeSent() {
        when(parameterSettingGroupService.getMail()).thenReturn(Mono.just(mail()));
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site()));
        when(senderFactory.create(any(ParameterSetting.Mail.class))).thenReturn(mailSender);

        StepVerifier.create(mailSettingsService.sendTest("admin@example.com")).verifyComplete();

        assertThat(capturedMessage().getSubject()).isEqualTo("PUDANS test email");
    }

    private SimpleMailMessage capturedMessage() {
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static ParameterSetting.Mail mail() {
        var mail = new ParameterSetting.Mail();
        mail.setMailFromName("PUDANS");
        mail.setMailFromAddress("no-reply@example.com");
        mail.setSmtpHost("smtp.example.com");
        mail.setSmtpPort(587);
        mail.setSmtpForceSsl(false);
        return mail;
    }

    private static ParameterSetting.Site site() {
        var site = new ParameterSetting.Site();
        site.setSiteName("PUDANS");
        site.setSiteUrl("https://pudans.example.com");
        return site;
    }
}
