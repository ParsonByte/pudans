package cn.tolim.pudans.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GatewayCorsConfigTests {

    @Autowired
    private GlobalCorsProperties globalCorsProperties;

    @Test
    void defaultCorsConfigurationAllowsHomeAndLoginOriginsWithCredentials() {
        CorsConfiguration configuration = globalCorsProperties.getCorsConfigurations().get("/**");

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .contains("http://localhost:3000", "http://127.0.0.1:3000");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).contains("*");
        assertThat(configuration.getExposedHeaders()).contains("Location");
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void wildcardOriginWithCredentialsIsRejected() {
        GlobalCorsProperties properties = new GlobalCorsProperties();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowCredentials(true);
        properties.getCorsConfigurations().put("/**", configuration);

        assertThatThrownBy(() -> GatewayCorsConfig.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard origin");
    }

    @Test
    void wildcardOriginWithoutCredentialsIsAllowed() {
        GlobalCorsProperties properties = new GlobalCorsProperties();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowCredentials(false);
        properties.getCorsConfigurations().put("/**", configuration);

        GatewayCorsConfig.validate(properties);
    }
}