package cn.tolim.pudans.iam.core.message.mail;

import cn.tolim.pudans.contract.notification.client.NotificationClient;
import cn.tolim.pudans.contract.notification.dto.NotificationApiResult;
import cn.tolim.pudans.contract.notification.dto.SendNotificationRequest;
import cn.tolim.pudans.contract.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.exception.MailMessageSendException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;

import static cn.tolim.pudans.iam.core.message.MsgVariable.CLIENT_NAME;
import static cn.tolim.pudans.iam.core.message.MsgVariable.EXPIRE_TIME_KEY;
import static cn.tolim.pudans.iam.core.message.MsgVariable.TIME;
import static cn.tolim.pudans.iam.core.message.MsgVariable.USER_EMAIL;
import static cn.tolim.pudans.iam.core.message.MsgVariable.VERIFY_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailMsgEventListenerTests {

    @Test
    void sendsRenderedEmailThroughNotificationService() {
        NotificationClient notificationClient = mock(NotificationClient.class);
        when(notificationClient.send(any(SendNotificationRequest.class)))
                .thenReturn(NotificationApiResult.ok(SendNotificationResponse.builder()
                        .status("SUCCESS").build()));
        MailMsgEventListener listener = listener(notificationClient);

        listener.onApplicationEvent(new MailMsgEvent(MailType.LOGIN, "alice@example.com",
                mailParameter()));

        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationClient).send(captor.capture());
        SendNotificationRequest request = captor.getValue();
        assertThat(request.getChannel()).isEqualTo("EMAIL");
        assertThat(request.getRecipient()).isEqualTo("alice@example.com");
        assertThat(request.getTemplateCode()).isNull();
        assertThat(request.getSubject())
                .isEqualTo("您正在登录PUDANS系统，验证码为：123456，该验证码5分钟内有效，请勿泄露于他人。");
        assertThat(request.getContent()).contains("123456");
        assertThat(request.getParameters()).containsEntry(VERIFY_CODE, "123456")
                .containsEntry(EXPIRE_TIME_KEY, "5")
                .containsEntry(USER_EMAIL, "alice@example.com");
    }

    @Test
    void throwsOriginalExceptionWhenNotificationCallFails() {
        NotificationClient notificationClient = mock(NotificationClient.class);
        when(notificationClient.send(any(SendNotificationRequest.class)))
                .thenReturn(NotificationApiResult.error("500", "notification service unavailable"));
        MailMsgEventListener listener = listener(notificationClient);

        assertThatThrownBy(() -> listener.onApplicationEvent(
                new MailMsgEvent(MailType.LOGIN, "alice@example.com", mailParameter())))
                .isInstanceOf(MailMessageSendException.class);
    }

    private MailMsgEventListener listener(NotificationClient notificationClient) {
        RedisTemplate<Object, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<Object, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        return new MailMsgEventListener(notificationClient, redisTemplate);
    }

    private Map<String, Object> mailParameter() {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put(VERIFY_CODE, "123456");
        parameter.put(EXPIRE_TIME_KEY, 5);
        parameter.put(TIME, "2026-11-28 10:00:00");
        parameter.put(CLIENT_NAME, "PUDANS");
        parameter.put(USER_EMAIL, "alice@example.com");
        return parameter;
    }
}