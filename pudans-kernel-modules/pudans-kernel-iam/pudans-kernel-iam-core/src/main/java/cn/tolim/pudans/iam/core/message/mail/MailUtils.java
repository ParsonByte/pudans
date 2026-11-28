package cn.tolim.pudans.iam.core.message.mail;

import cn.tolim.pudans.iam.communal.exception.MailTemplateException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.NonNull;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


public class MailUtils {
    // Reads and HTML-escapes an email template from the classpath.
    public static String readEmailContent(@NonNull String resourceLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(resourceLocation);
            InputStream inputStream = resource.getInputStream();
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            content = StringUtils.remove(content,
                    StringUtils.substringBetween(content, "<!--", "-->"));
            content = StringUtils.remove(StringUtils.remove(content, "<!--"), "-->");
            content = HtmlUtils.htmlEscape(content);
            return content;
        } catch (IOException e) {
            throw new MailTemplateException("获取文件模板失败", e);
        }
    }

}
