package cn.tolim.pudans.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class SandboxPropertiesConfigurationTest {

    // 校验 application.yaml 的沙箱键可完整绑定且默认值一致。
    @Test
    void bindsSandboxDefaultsFromApplicationYaml() throws Exception {
        var environment = new MockEnvironment();
        var sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        sources.forEach(source -> environment.getPropertySources().addFirst(source));

        var properties = Binder.get(environment)
                .bind("pudans.sandbox", SandboxProperties.class)
                .get();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getProvider()).isEqualTo("agentrun");
        assertThat(properties.getWorkspaceRoot()).isEqualTo("/home/pudans/workspace");
        assertThat(properties.getDefaultTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getMaxTtlSeconds()).isEqualTo(86_400);
        assertThat(properties.getLeaseTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getDefaultExecTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getMaxOutputBytes()).isEqualTo(65_536);
        assertThat(properties.getMaxFileBytes()).isEqualTo(524_288);
        assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.getReadTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getMaxRetries()).isEqualTo(3);
        assertThat(properties.getSnapshotTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.configured("agentrun")).isFalse();
        assertThat(properties.getE2b().getApiBaseUrl()).isEqualTo("https://api.e2b.app");
        assertThat(properties.getE2b().getDomain()).isEqualTo("e2b.app");
        assertThat(properties.getE2b().getTemplate()).isEqualTo("base");
        assertThat(properties.getE2b().getWorkspaceRoot()).isEqualTo("/home/user");
        assertThat(properties.getE2b().getRunUser()).isEqualTo("user");
        assertThat(properties.getE2b().getPersistenceMode()).isEqualTo("TAR");
        assertThat(properties.configured("e2b")).isFalse();
    }
}
