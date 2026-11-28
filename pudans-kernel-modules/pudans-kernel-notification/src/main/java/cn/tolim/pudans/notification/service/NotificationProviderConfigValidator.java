package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import cn.tolim.pudans.notification.sender.NotificationProviderConfig;
import cn.tolim.pudans.notification.sender.ProviderConfigKeys;
import org.springframework.stereotype.Component;

import java.util.Map;

// Validates provider settings before they are stored.
@Component
public class NotificationProviderConfigValidator {

    public void validate(NotificationChannel channel, ProviderType providerType,
                         Map<String, String> configuration) {
        if (channel == null) {
            throw new NotificationValidationException("Notification channel is required");
        }
        if (providerType == null) {
            throw new NotificationValidationException("Provider type is required");
        }
        if (!channelOf(providerType).equals(channel)) {
            throw new NotificationValidationException(
                    "Provider type " + providerType + " cannot serve channel " + channel);
        }
        NotificationProviderConfig config = new NotificationProviderConfig(configuration);
        switch (providerType) {
            case SMTP -> {
                config.require(ProviderConfigKeys.HOST);
                config.require(ProviderConfigKeys.FROM);
                int port = config.getInt(ProviderConfigKeys.PORT, 25);
                if (port < 1 || port > 65_535) {
                    throw new NotificationValidationException("SMTP port is out of range");
                }
            }
            case ALIYUN_SMS -> {
                config.require(ProviderConfigKeys.ACCESS_KEY_ID);
                config.require(ProviderConfigKeys.ACCESS_KEY_SECRET);
                config.require(ProviderConfigKeys.SIGN_NAME);
            }
            case TENCENT_SMS -> {
                config.require(ProviderConfigKeys.SECRET_ID);
                config.require(ProviderConfigKeys.SECRET_KEY);
                config.require(ProviderConfigKeys.SDK_APP_ID);
                config.require(ProviderConfigKeys.SIGN_NAME);
            }
            case QINIU_SMS -> {
                config.require(ProviderConfigKeys.ACCESS_KEY);
                config.require(ProviderConfigKeys.SECRET_KEY);
            }
            case FEISHU -> requireWebhookOrApp(config, false);
            case DINGTALK -> requireWebhookOrApp(config, true);
        }
    }

    public NotificationChannel channelOf(ProviderType providerType) {
        return switch (providerType) {
            case SMTP -> NotificationChannel.EMAIL;
            case ALIYUN_SMS, TENCENT_SMS, QINIU_SMS -> NotificationChannel.SMS;
            case FEISHU -> NotificationChannel.FEISHU;
            case DINGTALK -> NotificationChannel.DINGTALK;
        };
    }

    private void requireWebhookOrApp(NotificationProviderConfig config, boolean requiresAgent) {
        String webhookUrl = config.get(ProviderConfigKeys.WEBHOOK_URL);
        if (webhookUrl != null) {
            return;
        }
        config.require(ProviderConfigKeys.APP_ID);
        config.require(ProviderConfigKeys.APP_SECRET);
        if (requiresAgent) {
            config.require(ProviderConfigKeys.AGENT_ID);
        }
    }
}