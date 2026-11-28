package cn.tolim.pudans.communal.log;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

// Applies ECS structured logging defaults when pudans.log.structured is enabled.
public class PudansLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String STRUCTURED_ENABLED = "pudans.log.structured";
    private static final String APPLICATION_NAME = "spring.application.name";
    private static final String SERVICE_ENVIRONMENT = "pudans.log.environment";
    private static final String CONSOLE_FORMAT = "logging.structured.format.console";
    private static final String FILE_FORMAT = "logging.structured.format.file";
    private static final String LOG_FILE_NAME = "logging.file.name";
    private static final String ECS_SERVICE_NAME = "logging.structured.ecs.service.name";
    private static final String ECS_SERVICE_ENVIRONMENT = "logging.structured.ecs.service.environment";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Boolean.parseBoolean(environment.getProperty(STRUCTURED_ENABLED, "false"))) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(CONSOLE_FORMAT, "ecs");
        if (StringUtils.hasText(environment.getProperty(LOG_FILE_NAME))) {
            defaults.put(FILE_FORMAT, "ecs");
        }
        String applicationName = environment.getProperty(APPLICATION_NAME);
        if (applicationName != null && !applicationName.isBlank()) {
            defaults.put(ECS_SERVICE_NAME, applicationName);
        }
        String serviceEnvironment = environment.getProperty(SERVICE_ENVIRONMENT);
        if (serviceEnvironment != null && !serviceEnvironment.isBlank()) {
            defaults.put(ECS_SERVICE_ENVIRONMENT, serviceEnvironment);
        }
        environment.getPropertySources().addLast(new MapPropertySource("pudansLoggingDefaults", defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}