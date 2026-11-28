package cn.tolim.pudans.iam.core.message.sms;

import cn.tolim.pudans.contract.notification.client.NotificationClient;
import cn.tolim.pudans.contract.notification.dto.NotificationApiResult;
import cn.tolim.pudans.contract.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.contract.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.iam.communal.enums.SmsType;
import cn.tolim.pudans.iam.communal.exception.SmsMessageSendException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsMsgEventListenerTests {

    private final NotificationClient notificationClient = mock(NotificationClient.class);
    private final SmsMsgEventListener listener = new SmsMsgEventListener(notificationClient);

    @Test
    void sendsSmsThroughNotificationServiceWithProviderTemplateCode() {
        when(notificationClient.send(any(SendNotificationRequest.class)))
                .thenReturn(NotificationApiResult.ok(SendNotificationResponse.builder()
                        .status("SUCCESS").build()));
        LinkedHashMap<String, String> parameter = smsParameter();

        listener.onApplicationEvent(new SmsMsgEvent(SmsType.LOGIN, parameter));

        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationClient).send(captor.capture());
        SendNotificationRequest request = captor.getValue();
        assertThat(request.getChannel()).isEqualTo("SMS");
        assertThat(request.getRecipient()).isEqualTo("13800138000");
        assertThat(request.getProviderTemplateCode()).isEqualTo("SMS_100");
        assertThat(request.getTemplateCode()).isNull();
        assertThat(request.getParameters()).containsExactly(entry("verify_code", "123456"),
                entry("expire_time", "300"));
        assertThat(request.getTemplateParameters()).containsExactly("123456", "300");
        assertThat(parameter).containsKeys(SmsMsgEventPublish.PHONE,
                SmsMsgEventPublish.TEMPLATE_CODE, SmsMsgEventPublish.CONTENT);
    }

    @Test
    void throwsOriginalExceptionWhenNotificationCallFails() {
        when(notificationClient.send(any(SendNotificationRequest.class)))
                .thenReturn(NotificationApiResult.error("500", "notification service unavailable"));

        assertThatThrownBy(() -> listener.onApplicationEvent(
                new SmsMsgEvent(SmsType.LOGIN, smsParameter())))
                .isInstanceOf(SmsMessageSendException.class);
    }

    private LinkedHashMap<String, String> smsParameter() {
        LinkedHashMap<String, String> parameter = new LinkedHashMap<>();
        parameter.put("verify_code", "123456");
        parameter.put("expire_time", "300");
        parameter.put(SmsMsgEventPublish.PHONE, "13800138000");
        parameter.put(SmsMsgEventPublish.TEMPLATE_CODE, "SMS_100");
        parameter.put(SmsMsgEventPublish.CONTENT, "{...}");
        return parameter;
    }
}