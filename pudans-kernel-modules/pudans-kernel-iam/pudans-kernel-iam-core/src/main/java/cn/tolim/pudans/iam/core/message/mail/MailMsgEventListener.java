package cn.tolim.pudans.iam.core.message.mail;

import cn.tolim.pudans.contract.notification.NotificationContractConstants;
import cn.tolim.pudans.contract.notification.client.NotificationClient;
import cn.tolim.pudans.contract.notification.dto.NotificationApiResult;
import cn.tolim.pudans.contract.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.contract.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.iam.communal.entity.setting.MailTemplateEntity;
import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.exception.MailMessageSendException;
import cn.tolim.pudans.iam.core.message.MsgVariable;
import cn.tolim.pudans.iam.core.setting.MessageSettingConstants;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;
import static org.apache.commons.codec.CharEncoding.UTF_8;
import static org.springframework.web.util.HtmlUtils.htmlUnescape;


@Async
@Component
public class MailMsgEventListener implements ApplicationListener<MailMsgEvent> {
    private final Logger logger = LoggerFactory.getLogger(MailMsgEventListener.class);
    // Notification client
    private final NotificationClient notificationClient;
    // redis
    private final RedisTemplate<Object, Object> redisTemplate;
    private final Configuration freeMarkerConfiguration;
    public MailMsgEventListener(NotificationClient notificationClient,
                                RedisTemplate<Object, Object> redisTemplate) {
        this.notificationClient = notificationClient;
        this.redisTemplate = redisTemplate;
        freeMarkerConfiguration = new Configuration(Configuration.VERSION_2_3_31);
        // Set template loading directory
        freeMarkerConfiguration.setClassForTemplateLoading(this.getClass(), "/mail/content");
    }

    @Override
    public void onApplicationEvent(@NonNull MailMsgEvent event) {
        // Email notification type
        MailType type = event.getType();
        String content = htmlUnescape(MailUtils.readEmailContent(type.getContent()));
        String subject = type.getSubject();
        try {
            Map<String, Object> parameter = event.getParameter();
            // Check whether a custom value exists
            MailTemplateEntity templateEntity = (MailTemplateEntity) redisTemplate.opsForValue()
                    .get(MessageSettingConstants.SETTING_EMAIL_TEMPLATE_CACHE_NAME + COLON
                            + type.getCode());
            if (!Objects.isNull(templateEntity)) {
                content = htmlUnescape(templateEntity.getContent());
                subject = templateEntity.getSubject();
            }
            // Subject
            StringWriter themeStringWriter = new StringWriter();
            Template themeTpl = createTemplate(type.getCode() + "-theme", subject);
            themeTpl.process(parameter, themeStringWriter);
            // Test email
            if (parameter.containsKey(MsgVariable.TEST)) {
                String test = parameter.get(MsgVariable.TEST).toString();
                themeStringWriter.append(test);
            }
            // Content
            StringWriter contentStringWriter = new StringWriter();
            Template contentTpl = createTemplate(type.getCode() + "-content", content);
            contentTpl.process(parameter, contentStringWriter);
            // Send email through the notification service
            SendNotificationRequest request = SendNotificationRequest.builder()
                    // Recipient
                    .recipient(event.getReceiver())
                    // Channel
                    .channel(NotificationContractConstants.CHANNEL_EMAIL)
                    // Subject
                    .subject(themeStringWriter.toString())
                    // Content
                    .content(contentStringWriter.toString())
                    // Template parameters passed to the notification service
                    .parameters(toNotificationParameters(parameter))
                    .build();
            NotificationApiResult<SendNotificationResponse> result = notificationClient.send(request);
            if (Objects.isNull(result) || !Boolean.TRUE.equals(result.getSuccess())) {
                String message = Objects.isNull(result) ? "通知服务无响应" : result.getMessage();
                logger.error("邮件信息发送失败: {}", message);
                throw new MailMessageSendException(message);
            }
            // Delivery failures are kept in the notification service record
            SendNotificationResponse response = result.getResult();
            if (!Objects.isNull(response)
                    && (NotificationContractConstants.STATUS_FAILED.equals(response.getStatus())
                    || NotificationContractConstants.STATUS_DEAD_LETTER.equals(response.getStatus()))) {
                logger.error("邮件信息发送失败: status: {}, error: {}", response.getStatus(),
                        response.getLastError());
            }
        } catch (Exception e) {
            logger.error("邮件信息发送失败: {}", e.getMessage());
            throw new MailMessageSendException("邮件信息发送失败！", e);
        }
    }

    // Converts template parameters to notification string parameters
    // Parameter parameter: Map<String, Object>
    // Returns: Map<String, String>
    private Map<String, String> toNotificationParameters(Map<String, Object> parameter) {
        Map<String, String> result = new LinkedHashMap<>(parameter.size());
        parameter.forEach((key, value) -> result.put(key, Objects.isNull(value) ? "" : value.toString()));
        return result;
    }

    // Create template
    // Parameter name: String Template name
    // Parameter templateContent: String Template content
    // Returns: Template
    // Throws IOException: IOException
    private Template createTemplate(String name, String templateContent) throws IOException {
        try {
            Template template = freeMarkerConfiguration.getTemplate(name, UTF_8);
            if (template != null) {
                if (template.toString().trim().equals(templateContent.trim())) {
                    return template;
                }
            }
        } catch (Exception ignored) {
        }
        // The following operations are not thread-safe and require synchronization
        synchronized (this) {
            // Get template loader
            TemplateLoader templateLoader = freeMarkerConfiguration.getTemplateLoader();
            if (templateLoader instanceof StringTemplateLoader) {
                // If the loader is already a string loader, put the new template into the existing loader
                ((StringTemplateLoader) templateLoader).putTemplate(name, templateContent);
                freeMarkerConfiguration.setTemplateLoader(templateLoader);
            } else {
                // If the loader is not a string loader (the default is a file loader), create a new one
                StringTemplateLoader stringTemplateLoader = new StringTemplateLoader();
                stringTemplateLoader.putTemplate(name, templateContent);
                freeMarkerConfiguration.setTemplateLoader(stringTemplateLoader);
            }
            // Clear the cache here so the template can be retrieved below
            freeMarkerConfiguration.clearTemplateCache();

            return freeMarkerConfiguration.getTemplate(name, UTF_8);
        }
    }

}