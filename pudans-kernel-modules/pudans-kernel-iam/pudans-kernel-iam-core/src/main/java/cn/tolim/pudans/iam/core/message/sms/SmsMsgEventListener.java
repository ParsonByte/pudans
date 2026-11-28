package cn.tolim.pudans.iam.core.message.sms;

import cn.tolim.pudans.contract.notification.NotificationContractConstants;
import cn.tolim.pudans.contract.notification.client.NotificationClient;
import cn.tolim.pudans.contract.notification.dto.NotificationApiResult;
import cn.tolim.pudans.contract.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.contract.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.iam.communal.exception.SmsMessageSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


@Async
@Component
public class SmsMsgEventListener implements ApplicationListener<SmsMsgEvent> {
    // Logger
    private final Logger logger = LoggerFactory.getLogger(SmsMsgEventListener.class);
    // Notification client
    private final NotificationClient notificationClient;

    public SmsMsgEventListener(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    // Send event notification
    // Parameter event: SmsMsgEvent
    @Override
    public void onApplicationEvent(@NonNull SmsMsgEvent event) {
        Map<String, String> eventParameter = event.getParameter();
        // Phone number
        String phone = eventParameter.get(SmsMsgEventPublish.PHONE);
        // Provider template code
        String templateCode = eventParameter.get(SmsMsgEventPublish.TEMPLATE_CODE);
        // Remove the phone number, template code, and content
        Map<String, String> parameter = new LinkedHashMap<>(eventParameter);
        parameter.remove(SmsMsgEventPublish.PHONE);
        parameter.remove(SmsMsgEventPublish.TEMPLATE_CODE);
        parameter.remove(SmsMsgEventPublish.CONTENT);
        // SMS template parameters
        SendNotificationRequest request = SendNotificationRequest.builder()
                .recipient(phone)
                .channel(NotificationContractConstants.CHANNEL_SMS)
                .providerTemplateCode(templateCode)
                .parameters(parameter)
                .templateParameters(new ArrayList<>(parameter.values()))
                .build();
        try {
            NotificationApiResult<SendNotificationResponse> result = notificationClient.send(request);
            if (Objects.isNull(result) || !Boolean.TRUE.equals(result.getSuccess())) {
                String message = Objects.isNull(result) ? "通知服务无响应" : result.getMessage();
                logger.error("发送短信失败: params: {}, error: {}", request, message);
                throw new SmsMessageSendException(message);
            }
            // Delivery failures are kept in the notification service record
            SendNotificationResponse response = result.getResult();
            if (Objects.isNull(response)) {
                logger.error("发送短信失败,返回值为空: params: {}", request);
            } else if (NotificationContractConstants.STATUS_FAILED.equals(response.getStatus())
                    || NotificationContractConstants.STATUS_DEAD_LETTER.equals(response.getStatus())) {
                logger.error("发送短信失败: params: {}, response: {}", request,
                        response.getLastError());
            }
        } catch (Exception e) {
            logger.error("发送短信消息异常 params:{}, error: {}", request, e.getMessage());
            throw new SmsMessageSendException(e);
        }
    }
}