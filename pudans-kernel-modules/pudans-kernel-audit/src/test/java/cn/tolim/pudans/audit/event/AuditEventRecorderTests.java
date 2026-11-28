package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.Actor;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.type.EventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditEventRecorderTests {

    private final AuditEventPublish auditEventPublish = mock(AuditEventPublish.class);

    private final AuditEventRecorder auditEventRecorder = new AuditEventRecorder(auditEventPublish);

    @Test
    void recordsLoginSuccessWithApplicationAndSessionTargets() {
        Authentication authentication = mock(Authentication.class);

        auditEventRecorder.recordLoginSuccess(authentication, "order", "sid-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Target>> targetsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditEventPublish).publish(eq(EventType.LOGIN_SUCCESS), eq(authentication),
                eq(EventStatus.SUCCESS), targetsCaptor.capture());
        assertThat(targetsCaptor.getValue()).extracting(Target::getType)
                .containsExactly(TargetType.APPLICATION, TargetType.LOGIN_SESSION);
        assertThat(targetsCaptor.getValue()).extracting(Target::getId)
                .containsExactly("order", "sid-1");
    }

    @Test
    void recordsLoginFailureWithUsernameActor() {
        auditEventRecorder.recordLoginFailure("alice", "bad credentials", "order");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parametersCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Target>> targetsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Actor> actorCaptor = ArgumentCaptor.forClass(Actor.class);
        verify(auditEventPublish).publish(eq(EventType.LOGIN_FAILURE), parametersCaptor.capture(),
                contains("alice"), targetsCaptor.capture(), eq("bad credentials"),
                eq(EventStatus.FAIL), actorCaptor.capture());
        assertThat(actorCaptor.getValue().getId()).isEqualTo("alice");
        assertThat(parametersCaptor.getValue()).containsEntry("username", "alice")
                .containsEntry("reason", "bad credentials").containsEntry("appCode", "order");
        assertThat(targetsCaptor.getValue()).singleElement()
                .satisfies(target -> assertThat(target.getType()).isEqualTo(TargetType.APPLICATION));
    }

    @Test
    void recordsDeniedAuthorizationDecisionWithContext() {
        Actor actor = Actor.builder().id("user-1").build();

        auditEventRecorder.recordAuthorizationDecision(actor, "order:read", "order:order/1", false,
                "explicit deny");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> parametersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditEventPublish).publish(eq(EventType.AUTHZ_DECISION_DENY),
                parametersCaptor.capture(), contains("order:read"), eq(null), eq("explicit deny"),
                eq(EventStatus.FAIL), eq(actor));
        assertThat(parametersCaptor.getValue()).containsEntry("action", "order:read")
                .containsEntry("resource", "order:order/1").containsEntry("decision", "deny");
    }

    @Test
    void recordsIssuedTicketWithoutTicketValue() {
        Actor actor = Actor.builder().id("user-1").build();

        auditEventRecorder.recordTicketIssued(actor, "order", "sid-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Target>> targetsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditEventPublish).publish(eq(EventType.TICKET_ISSUED), anyMap(),
                eq("签发SSO票据"), targetsCaptor.capture(), eq(null), eq(EventStatus.SUCCESS),
                eq(actor));
        assertThat(targetsCaptor.getValue()).extracting(Target::getType)
                .contains(TargetType.APPLICATION, TargetType.LOGIN_SESSION);
    }

    @Test
    void recordsPolicyChangeWithPolicyTarget() {
        Actor actor = Actor.builder().id("admin-1").build();

        auditEventRecorder.recordPolicyChange(EventType.POLICY_UPDATE, actor, "policy-1",
                "Order read");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Target>> targetsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditEventPublish).publish(eq(EventType.POLICY_UPDATE), anyMap(),
                eq("修改授权策略"), targetsCaptor.capture(), eq(null), eq(EventStatus.SUCCESS),
                eq(actor));
        assertThat(targetsCaptor.getValue()).singleElement()
                .satisfies(target -> assertThat(target.getType()).isEqualTo(TargetType.POLICY));
    }

    @Test
    void rejectsNonSessionEventTypeForSessionLifecycle() {
        Actor actor = Actor.builder().id("user-1").build();

        assertThatThrownBy(() -> auditEventRecorder.recordSessionEvent(EventType.LOGIN_SUCCESS,
                actor, "sid-1", "unexpected", EventStatus.SUCCESS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}