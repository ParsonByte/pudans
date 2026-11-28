package cn.tolim.pudans.communal.log;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PudansLoggingEnvironmentPostProcessorTests {

    private final PudansLoggingEnvironmentPostProcessor postProcessor =
            new PudansLoggingEnvironmentPostProcessor();

    @Test
    @SuppressWarnings("deprecation")
    void isRegisteredInSpringFactories() {
        List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
                EnvironmentPostProcessor.class, PudansLoggingEnvironmentPostProcessorTests.class.getClassLoader());

        assertThat(factoryNames).contains(PudansLoggingEnvironmentPostProcessor.class.getName());
    }

    @Test
    void appliesEcsDefaultsWhenStructuredLoggingIsEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pudans.log.structured", "true")
                .withProperty("spring.application.name", "pudans-iam")
                .withProperty("pudans.log.environment", "staging");

        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.structured.ecs.service.name")).isEqualTo("pudans-iam");
        assertThat(environment.getProperty("logging.structured.ecs.service.environment")).isEqualTo("staging");
    }

    @Test
    void appliesEcsFileFormatWhenLogFileIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pudans.log.structured", "true")
                .withProperty("logging.file.name", "/var/log/pudans/iam.json");

        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.structured.format.file")).isEqualTo("ecs");
    }

    @Test
    void leavesFileFormatUnsetWhenNoLogFileIsConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pudans.log.structured", "true");

        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.file")).isNull();
    }

    @Test
    void doesNotOverrideExplicitConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pudans.log.structured", "true")
                .withProperty("logging.structured.format.console", "logstash");

        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    }

    @Test
    void doesNothingWhenStructuredLoggingIsDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("pudans.log.structured", "false")
                .withProperty("logging.structured.format.console", "logstash")
                .withProperty("logging.structured.format.file", "gelf");

        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
        assertThat(environment.getProperty("logging.structured.format.file")).isEqualTo("gelf");
    }
}