package cn.tolim.pudans;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication(scanBasePackages = "cn.tolim.pudans")
@ConfigurationPropertiesScan(basePackages = "cn.tolim.pudans.infra.properties")
public class Application {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Application.class)
                .applicationStartup(new BufferingApplicationStartup(1024))
                .run(args);
    }
}
