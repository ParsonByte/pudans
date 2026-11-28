package cn.tolim.pudans.communal.feign.configuration;

import cn.tolim.pudans.communal.feign.PudansErrorDecoder;
import cn.tolim.pudans.communal.feign.TracePropagationInterceptor;
import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PudansFeignAutoConfigurationTests {

    @Test
    void registersInterceptorDecoderAndNoRetryByDefault() {
        try (AnnotationConfigApplicationContext context = context(Map.of())) {
            assertThat(context.getBean(TracePropagationInterceptor.class)).isNotNull();
            assertThat(context.getBean(PudansErrorDecoder.class)).isNotNull();
            assertThat(context.getBean(Retryer.class)).isSameAs(Retryer.NEVER_RETRY);
            assertThat(context.getBeansOfType(Request.Options.class)).isEmpty();
        }
    }

    @Test
    void registersGlobalTimeoutsOnlyWhenOverridden() {
        try (AnnotationConfigApplicationContext context =
                     context(Map.of("pudans.feign.override-timeouts", "true"))) {
            assertThat(context.getBean(Request.Options.class)).isNotNull();
        }
    }

    @Test
    void canBeDisabled() {
        try (AnnotationConfigApplicationContext context =
                     context(Map.of("pudans.feign.enabled", "false"))) {
            assertThat(context.getBeansOfType(TracePropagationInterceptor.class)).isEmpty();
            assertThat(context.getBeansOfType(PudansErrorDecoder.class)).isEmpty();
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", properties));
        context.registerBean(ObjectMapper.class, () -> JsonMapper.builder().build());
        context.register(PudansFeignAutoConfiguration.class);
        context.refresh();
        return context;
    }
}