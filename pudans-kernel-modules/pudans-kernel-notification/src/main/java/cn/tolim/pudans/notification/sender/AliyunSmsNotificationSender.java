package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import org.springframework.stereotype.Component;

import java.util.Set;

// Sends SMS through the Alibaba Cloud Dysms API.
@Component
public class AliyunSmsNotificationSender implements NotificationSender {

    private static final String DEFAULT_ENDPOINT = "dysmsapi.aliyuncs.com";

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public Set<ProviderType> providerTypes() {
        return Set.of(ProviderType.ALIYUN_SMS);
    }

    @Override
    public SendResult send(NotificationMessage message, NotificationProviderConfig config) {
        try {
            String templateCode = message.getProviderTemplateCode() != null
                    ? message.getProviderTemplateCode()
                    : config.get(ProviderConfigKeys.TEMPLATE_CODE);
            if (templateCode == null) {
                throw new NotificationValidationException("Aliyun SMS template code is required");
            }
            Client client = new Client(aliyunConfig(config));
            com.aliyun.dysmsapi20170525.models.SendSmsRequest request =
                    new com.aliyun.dysmsapi20170525.models.SendSmsRequest()
                            .setPhoneNumbers(message.getRecipient())
                            .setSignName(config.require(ProviderConfigKeys.SIGN_NAME))
                            .setTemplateCode(templateCode)
                            .setTemplateParam(JSON.toJSONString(message.getParameters()));
            SendSmsResponse response = client.sendSmsWithOptions(request, new RuntimeOptions());
            String code = response.getBody().getCode();
            String responseBody = JSON.toJSONString(response.getBody());
            if ("OK".equals(code)) {
                return SendResult.success(response.getBody().getBizId(), responseBody);
            }
            return SendResult.failure(response.getBody().getMessage(), responseBody);
        } catch (TeaException exception) {
            return SendResult.failure(exception.getMessage());
        } catch (Exception exception) {
            return SendResult.failure(exception.getMessage());
        }
    }

    private Config aliyunConfig(NotificationProviderConfig config) {
        Config aliyunConfig = new Config();
        aliyunConfig.setAccessKeyId(config.require(ProviderConfigKeys.ACCESS_KEY_ID));
        aliyunConfig.setAccessKeySecret(config.require(ProviderConfigKeys.ACCESS_KEY_SECRET));
        String endpoint = config.get(ProviderConfigKeys.ENDPOINT);
        aliyunConfig.setEndpoint(endpoint == null ? DEFAULT_ENDPOINT : endpoint);
        return aliyunConfig;
    }
}