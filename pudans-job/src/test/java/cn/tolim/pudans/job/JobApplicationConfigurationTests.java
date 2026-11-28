package cn.tolim.pudans.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the host app configuration that Gradle and Spring Boot consume at runtime
class JobApplicationConfigurationTests {

    private static PropertySource<?> applicationYaml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).hasSize(1);
        return sources.getFirst();
    }

    @Test
    void configuresServerDatasourceAndLiquibase() throws IOException {
        PropertySource<?> yaml = applicationYaml();

        assertThat(yaml.getProperty("server.port")).isEqualTo(8094);
        assertThat(yaml.getProperty("spring.application.name")).isEqualTo("pudans-job");
        assertThat((String) yaml.getProperty("spring.datasource.url"))
                .contains("PUDANS_DB_URL")
                .contains("jdbc:postgresql://");
        assertThat((String) yaml.getProperty("spring.datasource.username"))
                .contains("PUDANS_DB_USER");
        assertThat(yaml.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(yaml.getProperty("spring.jpa.open-in-view")).isEqualTo(false);
        assertThat(yaml.getProperty("spring.liquibase.enabled")).isEqualTo(true);
        assertThat(yaml.getProperty("spring.liquibase.change-log"))
                .isEqualTo("classpath:job/changelog-master.xml");
        assertThat(String.valueOf(yaml.getProperty("spring.config.import")))
                .contains("nacos:")
                .contains("refreshEnabled=true");
        assertThat(yaml.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,metrics,prometheus");
    }

    @Test
    void nacosSeedConfiguresInternalTokenAndJobExecutor() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("pudans-job", new ClassPathResource("pudans-job.yaml"));
        assertThat(sources).hasSize(1);
        PropertySource<?> yaml = sources.getFirst();

        assertThat((String) yaml.getProperty("pudans.internal.token")).isNotBlank();
        assertThat(yaml.getProperty("pudans.job.executor.enabled")).isEqualTo(true);
        assertThat(yaml.getProperty("pudans.job.executor.auto-start")).isEqualTo(true);
    }

    @Test
    void scanningAndRepositoryAnnotationsCoverTheJobModule() {
        SpringBootApplication bootApplication =
                JobApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(bootApplication).isNotNull();
        assertThat(bootApplication.scanBasePackages())
                .contains("cn.tolim.pudans.job", "cn.tolim.pudans.communal.web");

        EntityScan entityScan = JobApplication.class.getAnnotation(EntityScan.class);
        assertThat(entityScan).isNotNull();
        assertThat(entityScan.basePackages()).contains("cn.tolim.pudans");

        EnableJpaRepositories jpaRepositories =
                JobApplication.class.getAnnotation(EnableJpaRepositories.class);
        assertThat(jpaRepositories).isNotNull();
        assertThat(jpaRepositories.basePackages()).contains("cn.tolim.pudans");

        assertThat(JobApplication.class.getAnnotation(EnableFeignClients.class))
                .isNotNull();
    }
}