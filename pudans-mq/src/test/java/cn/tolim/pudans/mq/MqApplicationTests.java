package cn.tolim.pudans.mq;

import cn.tolim.pudans.mq.constant.MqApiPaths;
import cn.tolim.pudans.mq.endpoint.MessageCenterEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// No database is expected here: the datasource is a non-connecting stub and schema management is disabled
@SpringBootTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "pudans.mq.scheduler-enabled=false"
})
class MqApplicationTests {

    @Autowired
    private MessageCenterEndpoint messageCenterEndpoint;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void contextLoads() {
        assertThat(messageCenterEndpoint).isNotNull();
    }

    @Test
    void mountsMessageCenterControllers() {
        assertThat(handlerMapping.getHandlerMethods().values())
                .anySatisfy(handler -> assertThat(handler.getBeanType())
                        .isEqualTo(MessageCenterEndpoint.class));

        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());
        assertThat(patterns).contains(
                MqApiPaths.MESSAGES,
                MqApiPaths.DEAD_LETTERS,
                MqApiPaths.STATS,
                MqApiPaths.CONSUMERS,
                MqApiPaths.DISPATCH);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubDataSourceConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/pudans_mq",
                    "pudans", "pudans");
        }
    }
}