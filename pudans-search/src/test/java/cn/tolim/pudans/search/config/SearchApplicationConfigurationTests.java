package cn.tolim.pudans.search.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the search app configuration without starting a real PostgreSQL instance
class SearchApplicationConfigurationTests {

    private final StandardEnvironment environment = loadEnvironment();

    @Test
    void servesSearchApiOnDedicatedPort() {
        assertThat(property("server.port")).isEqualTo("8095");
        assertThat(property("spring.application.name")).isEqualTo("pudans-search");
    }

    @Test
    void configuresPostgresDatasource() {
        assertThat(property("spring.datasource.url"))
                .startsWith("jdbc:postgresql://")
                .contains("pudans_search");
        assertThat(property("spring.datasource.username")).isNotBlank();
        assertThat(property("spring.datasource.password")).isNotBlank();
    }

    @Test
    void validatesSchemaThroughLiquibase() {
        assertThat(property("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(property("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(property("spring.liquibase.enabled")).isEqualTo("true");
        assertThat(property("spring.liquibase.change-log"))
                .isEqualTo("classpath:search/changelog-master.xml");
    }

    @Test
    void exposesHealthAndInfoActuatorEndpoints() {
        assertThat(property("management.endpoints.web.exposure.include"))
                .contains("health")
                .contains("info");
    }

    @Test
    void declaresNacosConfigImport() {
        assertThat(property("spring.config.import"))
                .contains("nacos:")
                .contains("refreshEnabled=true");
    }

    @Test
    void nacosSeedConfiguresInternalTokenSecurity() {
        StandardEnvironment seed = loadEnvironment("pudans-search.yaml");

        assertThat(seed.getProperty("pudans.internal.token")).isNotBlank();
        assertThat(seed.getProperty("pudans.search.auto-create-index")).isEqualTo("true");
    }

    private String property(String key) {
        return environment.getProperty(key);
    }

    private static StandardEnvironment loadEnvironment() {
        return loadEnvironment("application.yml");
    }

    private static StandardEnvironment loadEnvironment(String resource) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource(resource));
            StandardEnvironment environment = new StandardEnvironment();
            sources.forEach(source -> environment.getPropertySources().addLast(source));
            return environment;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read search " + resource, exception);
        }
    }
}