package cn.tolim.pudans.notification.configuration;

import cn.tolim.pudans.notification.security.NotificationSecretCipher;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(NotificationSecretCipher.class)
    public NotificationSecretCipher notificationSecretCipher(
            NotificationProperties notificationProperties,
            ObjectProvider<Spissatus> spissatusProvider) {
        return new NotificationSecretCipher(notificationProperties,
                spissatusProvider.getIfAvailable());
    }
}