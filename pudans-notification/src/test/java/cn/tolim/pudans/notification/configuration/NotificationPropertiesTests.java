package cn.tolim.pudans.notification.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies application.yml and the Nacos seed bind the disabled mail and SMS defaults.
class NotificationPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.config.location=classpath:/application.yml,classpath:/pudans-notification.yaml",
                    "spring.cloud.nacos.config.enabled=false");

    @Test
    void bindsDisabledMailAndSmsDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            NotificationProperties properties = context.getBean(NotificationProperties.class);

            assertThat(properties.getDefaultMaxAttempts()).isEqualTo(3);
            assertThat(properties.getMail().isEnabled()).isFalse();
            assertThat(properties.getMail().getFrom()).isEqualTo("no-reply@pudans.local");
            assertThat(properties.getSms().isEnabled()).isFalse();
            assertThat(properties.getSms().getDefaultProvider()).isEmpty();
            assertThat(properties.getRetry().isSchedulerEnabled()).isTrue();
            assertThat(properties.getRetry().getBatchSize()).isEqualTo(50);
            assertThat(properties.getCrypto().getKeyProvider()).isEqualTo("memory");
        });
    }

    @Test
    void bindsServiceWiringProperties() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.application.name"))
                    .isEqualTo("pudans-notification");
            assertThat(context.getEnvironment().getProperty("spring.liquibase.change-log"))
                    .isEqualTo("classpath:notification/changelog-master.xml");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .contains("pudans_notification");
            assertThat(context.getEnvironment().getProperty("spring.mail.host")).isNotNull();
            assertThat(context.getEnvironment().getProperty("pudans.internal.token"))
                    .isNotBlank();
        });
    }

    @Test
    void overridesChannelDefaultsFromProperties() {
        runner.withPropertyValues(
                        "pudans.notification.mail.enabled=true",
                        "pudans.notification.sms.enabled=true",
                        "pudans.notification.sms.default-provider=aliyun")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    NotificationProperties properties =
                            context.getBean(NotificationProperties.class);

                    assertThat(properties.getMail().isEnabled()).isTrue();
                    assertThat(properties.getSms().isEnabled()).isTrue();
                    assertThat(properties.getSms().getDefaultProvider()).isEqualTo("aliyun");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationProperties.class)
    static class PropertiesConfiguration {
    }
}