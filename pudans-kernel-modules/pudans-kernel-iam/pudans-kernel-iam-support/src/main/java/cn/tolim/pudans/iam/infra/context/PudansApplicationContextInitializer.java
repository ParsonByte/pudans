package cn.tolim.pudans.iam.infra.context;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class PudansApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ApplicationContextService.setApplicationContext(applicationContext);
    }
}
