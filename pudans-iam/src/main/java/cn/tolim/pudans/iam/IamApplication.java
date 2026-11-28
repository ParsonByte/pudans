package cn.tolim.pudans.iam;

import cn.tolim.pudans.iam.communal.exception.handler.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableCaching
@EnableFeignClients(basePackages = "cn.tolim.pudans.contract.repository")
@EntityScan(basePackages = "cn.tolim.pudans")
@EnableJpaRepositories(basePackages = "cn.tolim.pudans")
@ComponentScan(basePackages = "cn.tolim.pudans", excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = GlobalExceptionHandler.class))
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }

}
