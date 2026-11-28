package cn.tolim.pudans.iam.core.message.mail;

import cn.tolim.pudans.iam.communal.enums.MailType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static cn.tolim.pudans.iam.core.context.ContextService.getCodeValidTime;
import static cn.tolim.pudans.iam.core.message.MsgVariable.*;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.DEFAULT_DATE_TIME_FORMATTER;


@Component
@Slf4j
@AllArgsConstructor
public class MailMsgEventPublish {

    // ApplicationEventPublisher
    private final ApplicationEventPublisher applicationEventPublisher;

    // Publishes a verification code email.
    public void publishVerifyCode(String receiver, MailType type, String verifyCode) {
        // publish event
        HashMap<String, Object> parameter = new HashMap<>(16);
        parameter.put(VERIFY_CODE, verifyCode);
        parameter.put(EXPIRE_TIME_KEY, getCodeValidTime());
        publish(type, receiver, parameter);
    }

    // Publishes a mail notification event.
    public void publish(MailType type, String receiver, Map<String, Object> parameter) {
        if (StringUtils.isBlank(receiver)) {
            log.warn("发送邮件通知失败, 接受者为空, type: {}", type);
            return;
        }
        // Time point
        parameter.put(TIME, LocalDateTime.now().format(DEFAULT_DATE_TIME_FORMATTER));
        // Client name
        parameter.put(CLIENT_NAME, "PUDANS 企业数字身份管控平台");
        // Client description
        parameter.put(CLIENT_DESCRIPTION,
                "PUDANS 数字身份管控平台，简称：IAM，用于管理企业内员工账号、权限、身份认证、应用访问，帮助整合部署在本地或云端的内部办公系统、业务系统及三方 SaaS 系统的所有身份，实现一个账号打通所有应用的服务。");
        // Recipient
        parameter.put(USER_EMAIL, receiver);
        // publish event
        applicationEventPublisher.publishEvent(new MailMsgEvent(type, receiver, parameter));

    }
}