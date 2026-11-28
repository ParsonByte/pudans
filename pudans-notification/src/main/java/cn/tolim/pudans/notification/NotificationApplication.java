package cn.tolim.pudans.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "cn.tolim.pudans.notification", "cn.tolim.pudans.communal.web"})
@EntityScan(basePackages = "cn.tolim.pudans")
@EnableJpaRepositories(basePackages = "cn.tolim.pudans")
@EnableFeignClients(basePackages = "cn.tolim.pudans")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

}