package cn.tolim.pudans.iam.portal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PortalProperties.class)
public class PortalConfiguration {
}