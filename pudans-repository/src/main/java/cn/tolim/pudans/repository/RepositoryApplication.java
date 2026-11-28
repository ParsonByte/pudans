package cn.tolim.pudans.repository;

import cn.tolim.pudans.repository.repository.RepositoryFileRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"cn.tolim.pudans.repository", "cn.tolim.pudans.communal.web"})
@EntityScan(basePackages = "cn.tolim.pudans")
// RepositoryJpaAutoConfiguration already registers this module's repositories
@EnableJpaRepositories(basePackages = "cn.tolim.pudans",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = RepositoryFileRepository.class))
@EnableFeignClients(basePackages = "cn.tolim.pudans")
public class RepositoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepositoryApplication.class, args);
    }

}