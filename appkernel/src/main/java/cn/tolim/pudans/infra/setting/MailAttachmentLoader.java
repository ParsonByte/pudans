package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MailAttachmentLoader {

    public List<MailAttachment> load(ParameterSetting.Mail mail, String templateKey) {
        if (mail.getMailTemplateAttachments() == null) {
            return List.of();
        }
        var attachments = mail.getMailTemplateAttachments().get(templateKey);
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream().map(MailAttachmentLoader::load).toList();
    }

    private static MailAttachment load(ParameterSetting.MailTemplateAttachment attachment) {
        if (!StringUtils.hasText(attachment.getClasspath())) {
            throw new TemplateRenderException("A mail attachment requires a classpath");
        }
        var resource = new ClassPathResource(attachment.getClasspath());
        if (!resource.exists()) {
            throw new TemplateRenderException(
                    "Mail attachment not found on the classpath: " + attachment.getClasspath());
        }
        try (var input = resource.getInputStream()) {
            var fileName = StringUtils.hasText(attachment.getFileName())
                    ? attachment.getFileName()
                    : resource.getFilename();
            return new MailAttachment(fileName == null ? "attachment" : fileName, input.readAllBytes());
        } catch (IOException e) {
            throw new TemplateRenderException("Unable to read mail attachment: " + attachment.getClasspath());
        }
    }

    public record MailAttachment(String fileName, byte[] content) {}
}
