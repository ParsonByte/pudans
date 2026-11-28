package cn.tolim.pudans.notification.template;

import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.exception.NotificationTemplateRenderException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTemplateRendererTests {

    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void rendersSubjectAndContentWithParameters() {
        NotificationTemplateEntity template = template("Hello ${name}",
                "Your code is ${code}.");

        RenderedNotification rendered = renderer.render(template,
                Map.of("name", "Alice", "code", "123456"));

        assertThat(rendered.subject()).isEqualTo("Hello Alice");
        assertThat(rendered.content()).isEqualTo("Your code is 123456.");
    }

    @Test
    void supportsFreeMarkerDefaultsForOptionalParameters() {
        NotificationTemplateEntity template = template("Welcome ${name!\"customer\"}",
                "Balance: ${balance!\"0\"}");

        RenderedNotification rendered = renderer.render(template, Map.of());

        assertThat(rendered.subject()).isEqualTo("Welcome customer");
        assertThat(rendered.content()).isEqualTo("Balance: 0");
    }

    @Test
    void keepsNullSubjectForSmsTemplates() {
        NotificationTemplateEntity template = template(null, "Code ${code}");
        template.setChannel(NotificationChannel.SMS);

        RenderedNotification rendered = renderer.render(template, Map.of("code", "7788"));

        assertThat(rendered.subject()).isNull();
        assertThat(rendered.content()).isEqualTo("Code 7788");
    }

    @Test
    void failsWhenARequiredParameterIsMissing() {
        NotificationTemplateEntity template = template("Hello ${name}", "Content");

        assertThatThrownBy(() -> renderer.render(template, Map.of()))
                .isInstanceOf(NotificationTemplateRenderException.class)
                .hasMessageContaining("Unable to render notification template");
    }

    @Test
    void returnsBlankWhenTemplateHasNoContent() {
        RenderedNotification rendered = renderer.render(null, Map.of());

        assertThat(rendered.subject()).isNull();
        assertThat(rendered.content()).isNull();
    }

    private NotificationTemplateEntity template(String subject, String content) {
        NotificationTemplateEntity template = new NotificationTemplateEntity();
        template.setCode("welcome");
        template.setName("Welcome");
        template.setChannel(NotificationChannel.EMAIL);
        template.setSubject(subject);
        template.setContent(content);
        template.setEnabled(true);
        return template;
    }
}