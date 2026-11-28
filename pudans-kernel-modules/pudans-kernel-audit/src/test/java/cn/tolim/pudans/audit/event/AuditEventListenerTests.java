package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.Actor;
import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.entity.Event;
import cn.tolim.pudans.audit.entity.GeoLocation;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.entity.UserAgent;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.audit.repository.AuditRepository;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventListenerTests {

    private final AuditRepository auditRepository = mock(AuditRepository.class);

    private final AuditEventListener auditEventListener = new AuditEventListener(auditRepository);

    @Test
    void persistsPublishedEventWithAllContext() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 11, 27, 10, 30);
        Event event = Event.builder().type(EventType.TICKET_EXCHANGED)
                .status(EventStatus.SUCCESS).time(eventTime).content("ticket exchanged")
                .param("{\"appCode\":\"order\"}").result("ok").build();
        Actor actor = Actor.builder().id("user-1").type(UserType.USER).authType("password").build();
        UserAgent userAgent = UserAgent.builder().browser("Chrome").browserType("chromium")
                .platform("macOS").build();
        GeoLocation geoLocation = GeoLocation.builder().ip("10.0.0.1").countryCode("CN").build();
        Target target = Target.builder().id("order").name("Order")
                .type(TargetType.APPLICATION).build();
        AuditEvent auditEvent = new AuditEvent("req-1", "sid-1", actor, event, userAgent,
                geoLocation, List.of(target));

        auditEventListener.onApplicationEvent(auditEvent);

        ArgumentCaptor<AuditEntity> captor = ArgumentCaptor.forClass(AuditEntity.class);
        verify(auditRepository).save(captor.capture());
        AuditEntity entity = captor.getValue();
        assertThat(entity.getRequestId()).isEqualTo("req-1");
        assertThat(entity.getSessionId()).isEqualTo("sid-1");
        assertThat(entity.getActorId()).isEqualTo("user-1");
        assertThat(entity.getActorType()).isEqualTo("user");
        assertThat(entity.getActorAuthType()).isEqualTo("password");
        assertThat(entity.getEventType()).isEqualTo(EventType.TICKET_EXCHANGED);
        assertThat(entity.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(entity.getEventContent()).isEqualTo("ticket exchanged");
        assertThat(entity.getEventParam()).isEqualTo("{\"appCode\":\"order\"}");
        assertThat(entity.getEventResult()).isEqualTo("ok");
        assertThat(entity.getEventTime()).isEqualTo(eventTime);
        assertThat(entity.getTargets()).singleElement()
                .satisfies(value -> assertThat(value.getType()).isEqualTo(TargetType.APPLICATION));
        assertThat(entity.getUserAgent().getBrowser()).isEqualTo("Chrome");
        assertThat(entity.getGeoLocation().getIp()).isEqualTo("10.0.0.1");
        assertThat(entity.getCreateBy()).isEqualTo("user-1");
        assertThat(entity.getUpdateBy()).isEqualTo("user-1");
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void usesSystemOperatorWhenActorIsUnknown() {
        Event event = Event.builder().type(EventType.LOGIN_FAILURE).status(EventStatus.FAIL)
                .time(LocalDateTime.now()).content("login failed").build();
        AuditEvent auditEvent = new AuditEvent("req-2", null, null, event, null, null, null);

        auditEventListener.onApplicationEvent(auditEvent);

        ArgumentCaptor<AuditEntity> captor = ArgumentCaptor.forClass(AuditEntity.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getCreateBy()).isEqualTo("system");
        assertThat(captor.getValue().getActorId()).isNull();
    }

    @Test
    void doesNotPropagatePersistenceFailure() {
        Event event = Event.builder().type(EventType.SESSION_EXPIRED).status(EventStatus.SUCCESS)
                .time(LocalDateTime.now()).build();
        AuditEvent auditEvent = new AuditEvent("req-3", "sid-3", null, event, null, null, null);
        when(auditRepository.save(any(AuditEntity.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> auditEventListener.onApplicationEvent(auditEvent))
                .doesNotThrowAnyException();
    }
}