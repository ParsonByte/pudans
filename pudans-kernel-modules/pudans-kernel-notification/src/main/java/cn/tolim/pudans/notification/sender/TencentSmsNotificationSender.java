package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Sends SMS through the Tencent Cloud SMS API.
@Component
public class TencentSmsNotificationSender implements NotificationSender {

    private static final String DEFAULT_REGION = "ap-guangzhou";

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public Set<ProviderType> providerTypes() {
        return Set.of(ProviderType.TENCENT_SMS);
    }

    @Override
    public SendResult send(NotificationMessage message, NotificationProviderConfig config) {
        try {
            String templateCode = message.getProviderTemplateCode() != null
                    ? message.getProviderTemplateCode()
                    : config.get(ProviderConfigKeys.TEMPLATE_CODE);
            if (templateCode == null) {
                throw new NotificationValidationException("Tencent SMS template code is required");
            }
            Credential credential = new Credential(config.require(ProviderConfigKeys.SECRET_ID),
                    config.require(ProviderConfigKeys.SECRET_KEY));
            String region = config.get(ProviderConfigKeys.REGION);
            SmsClient client = new SmsClient(credential,
                    region == null ? DEFAULT_REGION : region);
            com.tencentcloudapi.sms.v20210111.models.SendSmsRequest request =
                    new com.tencentcloudapi.sms.v20210111.models.SendSmsRequest();
            request.setSmsSdkAppId(config.require(ProviderConfigKeys.SDK_APP_ID));
            request.setSignName(config.require(ProviderConfigKeys.SIGN_NAME));
            request.setTemplateId(templateCode);
            request.setTemplateParamSet(templateParameters(message));
            request.setPhoneNumberSet(new String[]{phoneNumber(message.getRecipient())});
            com.tencentcloudapi.sms.v20210111.models.SendSmsResponse response =
                    client.SendSms(request);
            SendStatus[] statuses = response.getSendStatusSet();
            String responseBody = com.tencentcloudapi.sms.v20210111.models.SendSmsResponse
                    .toJsonString(response);
            if (statuses != null && statuses.length > 0) {
                SendStatus status = statuses[0];
                if ("Ok".equals(status.getCode())) {
                    return SendResult.success(status.getSerialNo(), responseBody);
                }
                return SendResult.failure(status.getMessage(), responseBody);
            }
            return SendResult.failure("Tencent SMS returned no send status", responseBody);
        } catch (TencentCloudSDKException exception) {
            return SendResult.failure(exception.getMessage());
        } catch (Exception exception) {
            return SendResult.failure(exception.getMessage());
        }
    }

    private String[] templateParameters(NotificationMessage message) {
        if (!message.getTemplateParameters().isEmpty()) {
            return message.getTemplateParameters().toArray(new String[0]);
        }
        List<String> values = new ArrayList<>(message.getParameters().values());
        return values.toArray(new String[0]);
    }

    private String phoneNumber(String recipient) {
        return recipient.startsWith("+") ? recipient : "+86" + recipient;
    }
}