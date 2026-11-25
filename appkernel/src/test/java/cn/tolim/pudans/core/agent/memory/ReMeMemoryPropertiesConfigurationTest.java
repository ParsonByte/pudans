package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ReMeMemoryPropertiesConfigurationTest {

    @Test
    void bindsDisabledReMeDefaultsFromApplicationYaml() throws Exception {
        var environment = new MockEnvironment();
        var sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        sources.forEach(source -> environment.getPropertySources().addFirst(source));

        var properties = Binder.get(environment)
                .bind("pudans.memory.reme", ReMeMemoryProperties.class)
                .get();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiBaseUrl()).isEmpty();
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getTopK()).isEqualTo(5);
        assertThat(properties.getMaxContextChars()).isEqualTo(8_000);
        assertThat(properties.getWorkspacePrefix()).isEqualTo("pudans");
        assertThat(properties.configured()).isFalse();
    }
}
