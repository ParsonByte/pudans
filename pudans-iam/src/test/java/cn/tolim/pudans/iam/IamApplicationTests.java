package cn.tolim.pudans.iam;

import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.AuditEventPublish;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.audit.repository.AuditRepository;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class IamApplicationTests {

    @Autowired
    private AuditEventPublish auditEventPublish;

    @Autowired
    private AuditRepository auditRepository;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void persistsPublishedAuditEvent() {
        UserDetails principal = new UserDetails("audit-user-id", "audit-user", UserType.USER, true,
                true, true, true, List.of());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, "", List.of());
        Target target = Target.builder().id("audit-app-id").name("Audit App")
                .type(TargetType.APPLICATION).build();

        auditEventPublish.publish(EventType.APP_SSO, authentication, EventStatus.SUCCESS,
                List.of(target));

        List<AuditEntity> records = auditRepository.findAll();
        assertThat(records).anySatisfy(record -> {
            assertThat(record.getEventType()).isEqualTo(EventType.APP_SSO);
            assertThat(record.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
            assertThat(record.getActorId()).isEqualTo("audit-user-id");
            assertThat(record.getActorType()).isEqualTo("user");
            assertThat(record.getTargets()).singleElement().satisfies(value -> {
                assertThat(value.getId()).isEqualTo("audit-app-id");
                assertThat(value.getType()).isEqualTo(TargetType.APPLICATION);
            });
        });
    }

}