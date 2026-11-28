package cn.tolim.pudans.notification.template;

import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import cn.tolim.pudans.notification.exception.NotificationTemplateRenderException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

// Renders notification subjects and bodies with FreeMarker.
@Component
public class NotificationTemplateRenderer {

    private final Configuration configuration;

    private final AtomicLong sequence = new AtomicLong();

    public NotificationTemplateRenderer() {
        this.configuration = new Configuration(Configuration.VERSION_2_3_31);
        this.configuration.setDefaultEncoding("UTF-8");
        this.configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        this.configuration.setLogTemplateExceptions(false);
        this.configuration.setWrapUncheckedExceptions(true);
    }

    public RenderedNotification render(NotificationTemplateEntity template,
                                       Map<String, String> parameters) {
        if (template == null) {
            return new RenderedNotification(null, null);
        }
        Map<String, Object> model = new HashMap<>();
        if (parameters != null) {
            model.putAll(parameters);
        }
        return new RenderedNotification(renderText(template.getSubject(), model),
                renderText(template.getContent(), model));
    }

    public String renderText(String source, Map<String, Object> parameters) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        try {
            String name = "notification-" + sequence.incrementAndGet();
            Template template = new Template(name, new StringReader(source), configuration);
            StringWriter writer = new StringWriter();
            template.process(parameters == null ? Map.of() : parameters, writer);
            return writer.toString();
        } catch (Exception exception) {
            throw new NotificationTemplateRenderException("Unable to render notification template",
                    exception);
        }
    }

    public String renderText(String source, Map<String, Object> parameters, String name) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        try {
            Template template = new Template(name, new StringReader(source), configuration);
            StringWriter writer = new StringWriter();
            template.process(parameters == null ? Map.of() : parameters, writer);
            return writer.toString();
        } catch (Exception exception) {
            throw new NotificationTemplateRenderException(
                    "Unable to render notification template " + name, exception);
        }
    }
}