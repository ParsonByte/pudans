package cn.tolim.pudans.iam.core.message.sms;

import cn.tolim.pudans.iam.communal.entity.setting.config.SmsConfig;
import cn.tolim.pudans.iam.communal.enums.SmsType;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static cn.tolim.pudans.iam.core.context.ContextService.getCodeValidTime;
import static cn.tolim.pudans.iam.core.context.ContextService.getSmsProviderConfig;
import static cn.tolim.pudans.iam.core.message.MsgVariable.EXPIRE_TIME_KEY;
import static cn.tolim.pudans.iam.core.message.MsgVariable.VERIFY_CODE;


@Component
@Slf4j
@AllArgsConstructor
public class SmsMsgEventPublish {
    public static final String TEMPLATE_CODE = "template_code";

    public static final String CONTENT = "content";

    public static final String PHONE = "phone";

    public static final String USERNAME = "username";
    // ApplicationEventPublisher
    private final ApplicationEventPublisher applicationEventPublisher;

    // Publishes a verification code SMS.
    public void publishVerifyCode(String phone, SmsType type, String code) {
        // publish event
        LinkedHashMap<String, String> parameter = new LinkedHashMap<>(16);
        parameter.put(VERIFY_CODE, code);
        parameter.put(EXPIRE_TIME_KEY, String.valueOf(getCodeValidTime()));
        publish(type, phone, parameter);
    }

    // Publishes an SMS notification event.
    @SneakyThrows
    public void publish(SmsType type, String phone, LinkedHashMap<String, String> parameter) {
        if (StringUtils.isBlank(phone)) {
            log.warn("发送短信通知失败, 接受者为空, type: {}", type);
            return;
        }
        parameter.put(PHONE, phone);
        // Find the template for the message type
        SmsConfig smsConfig = getSmsProviderConfig();
        List<SmsConfig.TemplateConfig> templates = smsConfig.getTemplates();
        if (CollectionUtils.isEmpty(templates)) {
            throw new PudansException("未配置[" + type.getDesc() + "]短信模板");
        }
        Optional<SmsConfig.TemplateConfig> template = templates.stream()
                .filter(item -> type == item.getType()).findFirst();
        SmsConfig.TemplateConfig templateConfig = template
                .orElseThrow(() -> new PudansException("未配置[" + type.getDesc() + "]短信模板"));
        parameter.put(TEMPLATE_CODE, templateConfig.getCode());
        parameter.put(CONTENT, JSON.toJSONString(parameter));
        // publish event
        applicationEventPublisher.publishEvent(new SmsMsgEvent(type, parameter));
    }
}
