package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.infra.api.ParameterSetting;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MailAttachmentLoaderTest {

    private final MailAttachmentLoader loader = new MailAttachmentLoader();

    @Test
    void loadsAClasspathAttachment() {
        var attachment = new ParameterSetting.MailTemplateAttachment();
        attachment.setFileName("notes.txt");
        attachment.setClasspath("mail/attachment.txt");
        var mail = new ParameterSetting.Mail();
        mail.setMailTemplateAttachments(Map.of("test", List.of(attachment)));

        var loaded = loader.load(mail, "test");

        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().fileName()).isEqualTo("notes.txt");
        assertThat(new String(loaded.getFirst().content())).contains("attachment-content");
    }

    @Test
    void missingAttachmentsAreRejected() {
        var attachment = new ParameterSetting.MailTemplateAttachment();
        attachment.setClasspath("mail/missing.txt");
        var mail = new ParameterSetting.Mail();
        mail.setMailTemplateAttachments(Map.of("test", List.of(attachment)));

        assertThatThrownBy(() -> loader.load(mail, "test"))
                .isInstanceOf(TemplateRenderException.class)
                .hasMessageContaining("mail/missing.txt");
    }
}
