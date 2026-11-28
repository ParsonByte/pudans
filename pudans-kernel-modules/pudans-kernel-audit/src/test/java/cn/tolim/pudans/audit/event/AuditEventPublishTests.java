package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.Actor;
import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.audit.repository.AuditRepository;
import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventPublishTests {

    @Test
    void publishesAuditEventWithActorAndTarget() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AuditEventPublish auditEventPublish = new AuditEventPublish(publisher,
                emptyProvider(), emptyProvider());
        UserDetails principal = new UserDetails("user-id", "alice", UserType.USER, true, true, true,
                true, List.of());
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "",
                List.of());
        Target target = Target.builder().id("app-id").name("Portal")
                .type(TargetType.APPLICATION).build();

        auditEventPublish.publish(EventType.APP_SSO, authentication, EventStatus.SUCCESS,
                List.of(target));

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        AuditEvent event = (AuditEvent) captor.getValue();
        assertThat(event.getEvent().getType()).isEqualTo(EventType.APP_SSO);
        assertThat(event.getEvent().getStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(event.getTargets()).containsExactly(target);
        assertThat(event.getActor().getId()).isEqualTo("user-id");
        assertThat(event.getActor().getType()).isEqualTo(UserType.USER);
    }

    @Test
    void keepsLegacyCodesForSsoEvents() {
        assertThat(EventType.APP_SSO.getCode()).isEqualTo("app_sso");
        assertThat(EventType.APP_SLO.getCode()).isEqualTo("app_slo");
    }

    @Test
    void persistsPublishedAuditEvent() {
        AuditRepository repository = mock(AuditRepository.class);
        AuditEventListener listener = new AuditEventListener(repository);
        Actor actor = Actor.builder().id("user-id").type(UserType.USER).authType("password")
                .build();
        cn.tolim.pudans.audit.entity.Event event = cn.tolim.pudans.audit.entity.Event.builder()
                .type(EventType.APP_SSO).status(EventStatus.SUCCESS).content("alice: single sign-on")
                .build();
        AuditEvent auditEvent = new AuditEvent("request-id", "session-id", actor, event, null, null,
                List.of());

        listener.onApplicationEvent(auditEvent);

        ArgumentCaptor<AuditEntity> captor = ArgumentCaptor.forClass(AuditEntity.class);
        verify(repository).save(captor.capture());
        AuditEntity entity = captor.getValue();
        assertThat(entity.getEventType()).isEqualTo(EventType.APP_SSO);
        assertThat(entity.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
        assertThat(entity.getActorId()).isEqualTo("user-id");
        assertThat(entity.getActorType()).isEqualTo("user");
        assertThat(entity.getCreateBy()).isEqualTo("user-id");
        assertThat(entity.getRequestId()).isEqualTo("request-id");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}