package cn.tolim.pudans.iam.core.configuration;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

// Registers the notification service contract client without touching the application class
@Configuration
@EnableFeignClients(basePackages = "cn.tolim.pudans.contract.notification")
public class IamNotificationFeignConfiguration {
}