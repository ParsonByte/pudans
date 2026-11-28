package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.sms.SmsManager;
import com.qiniu.util.Auth;
import org.springframework.stereotype.Component;

import java.util.Set;

// Sends SMS through the Qiniu Cloud SMS API.
@Component
public class QiniuSmsNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public Set<ProviderType> providerTypes() {
        return Set.of(ProviderType.QINIU_SMS);
    }

    @Override
    public SendResult send(NotificationMessage message, NotificationProviderConfig config) {
        try {
            String templateCode = message.getProviderTemplateCode() != null
                    ? message.getProviderTemplateCode()
                    : config.get(ProviderConfigKeys.TEMPLATE_CODE);
            if (templateCode == null) {
                throw new NotificationValidationException("Qiniu SMS template code is required");
            }
            Auth auth = Auth.create(config.require(ProviderConfigKeys.ACCESS_KEY),
                    config.require(ProviderConfigKeys.SECRET_KEY));
            SmsManager smsManager = new SmsManager(auth);
            Response response = smsManager.sendSingleMessage(templateCode, message.getRecipient(),
                    message.getParameters());
            String responseBody = response.bodyString();
            JSONObject document = JSON.parseObject(responseBody);
            String messageId = document.getString("message_id");
            if (messageId != null && !messageId.isBlank()) {
                return SendResult.success(messageId, responseBody);
            }
            return SendResult.failure("Qiniu SMS rejected the message", responseBody);
        } catch (QiniuException exception) {
            return SendResult.failure(exception.getMessage());
        } catch (Exception exception) {
            return SendResult.failure(exception.getMessage());
        }
    }
}