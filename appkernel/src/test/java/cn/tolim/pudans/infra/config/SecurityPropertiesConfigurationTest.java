package cn.tolim.pudans.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SecurityPropertiesConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(SecurityPropertiesConfiguration.class, PropertiesConfiguration.class)
            .withPropertyValues("pudans.work-dir=/tmp/pudans");

    // 配置属性扫描只能注册一个 PudansProperties。
    @Test
    void bindsSinglePudansPropertiesBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(PudansProperties.class));
    }

    // 安全配置必须复用已绑定的配置属性。
    @Test
    void exposesBoundSecurityProperties() {
        contextRunner.run(context -> assertThat(context.getBean(SecurityProperties.class))
                .isSameAs(context.getBean(PudansProperties.class).getSecurity()));
    }

    // 配置属性仍要执行自定义校验。
    @Test
    void validatesAbsolutePermalinkExternalUrl() {
        contextRunner
                .withPropertyValues("pudans.use-absolute-permalink=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("当属性 `use-absolute-permalink` 设置为 true 时必须提供外部 URL。"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PudansProperties.class)
    static class PropertiesConfiguration {}
}
