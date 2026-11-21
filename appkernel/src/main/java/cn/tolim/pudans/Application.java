package cn.tolim.pudans;

import cn.tolim.pudans.infra.utils.PudansTime;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "cn.tolim.pudans")
@ConfigurationPropertiesScan(basePackages = "cn.tolim.pudans.infra.properties")
public class Application {

    // 应用启动入口：先固定时区，再启动 Spring 容器。
    public static void main(String[] args) {
        PudansTime.applySystemTimeZone();
        new SpringApplicationBuilder(Application.class)
                .applicationStartup(new BufferingApplicationStartup(1024))
                .run(args);
    }
}
