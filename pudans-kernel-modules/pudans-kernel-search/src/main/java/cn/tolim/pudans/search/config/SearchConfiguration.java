package cn.tolim.pudans.search.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfiguration {
}