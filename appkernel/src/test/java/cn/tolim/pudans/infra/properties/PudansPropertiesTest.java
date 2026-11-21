package cn.tolim.pudans.infra.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class PudansPropertiesTest {

    // 使用绝对永久链接时必须配置外部 URL。
    @Test
    void requiresExternalUrlForAbsolutePermalinks() {
        var properties = new PudansProperties();
        properties.setUseAbsolutePermalink(true);
        var errors = new BeanPropertyBindingResult(properties, "pudans");

        properties.validate(properties, errors);

        assertThat(errors.hasFieldErrors("externalUrl")).isTrue();
    }

    // 外部 URL 必须是 HTTP(S) 地址。
    @Test
    void rejectsNonHttpExternalUrl() throws MalformedURLException {
        var properties = new PudansProperties();
        properties.setExternalUrl(URI.create("ftp://example.com").toURL());
        var errors = new BeanPropertyBindingResult(properties, "pudans");

        properties.validate(properties, errors);

        assertThat(errors.hasFieldErrors("externalUrl")).isTrue();
    }

    // 合法的 HTTPS 地址通过校验。
    @Test
    void acceptsHttpsExternalUrl() throws MalformedURLException {
        var properties = new PudansProperties();
        properties.setExternalUrl(URI.create("https://pudans.example.com").toURL());
        var errors = new BeanPropertyBindingResult(properties, "pudans");

        properties.validate(properties, errors);

        assertThat(errors.hasFieldErrors("externalUrl")).isFalse();
    }

    // 校验器只处理当前配置类型。
    @Test
    void supportsOnlyPudansProperties() {
        var properties = new PudansProperties();

        assertThat(properties.supports(PudansProperties.class)).isTrue();
        assertThat(properties.supports(String.class)).isFalse();
    }
}
