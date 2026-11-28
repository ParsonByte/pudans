package cn.tolim.pudans.audit.enums;

import cn.tolim.pudans.audit.event.type.EventType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCategoryTests {

    @Test
    void everyEventTypeHasACategory() {
        for (EventType eventType : EventType.values()) {
            assertThat(AuditCategory.from(eventType)).isNotNull();
        }
        assertThat(AuditCategory.typesOf(null)).isEmpty();
    }

    @Test
    void mapsLifecycleEventTypesToCategories() {
        assertThat(AuditCategory.from(EventType.LOGIN_SUCCESS)).isEqualTo(AuditCategory.LOGIN);
        assertThat(AuditCategory.from(EventType.LOGIN_PORTAL)).isEqualTo(AuditCategory.LOGIN);
        assertThat(AuditCategory.from(EventType.GLOBAL_LOGOUT)).isEqualTo(AuditCategory.LOGOUT);
        assertThat(AuditCategory.from(EventType.LOGOUT_CONSOLE)).isEqualTo(AuditCategory.LOGOUT);
        assertThat(AuditCategory.from(EventType.SESSION_KICKED)).isEqualTo(AuditCategory.SESSION);
        assertThat(AuditCategory.from(EventType.SESSION_MFA_RESET)).isEqualTo(AuditCategory.SESSION);
        assertThat(AuditCategory.from(EventType.BACKCHANNEL_LOGOUT_SENT))
                .isEqualTo(AuditCategory.TICKET);
        assertThat(AuditCategory.from(EventType.TICKET_ISSUED)).isEqualTo(AuditCategory.TICKET);
        assertThat(AuditCategory.from(EventType.AUTHZ_DECISION_DENY))
                .isEqualTo(AuditCategory.AUTHORIZATION);
        assertThat(AuditCategory.from(EventType.POLICY_UPDATE))
                .isEqualTo(AuditCategory.AUTHORIZATION);
        assertThat(AuditCategory.from(EventType.AUTHZ_ROLE_BIND))
                .isEqualTo(AuditCategory.AUTHORIZATION);
        assertThat(AuditCategory.from(EventType.APP_SSO)).isEqualTo(AuditCategory.TICKET);
        assertThat(AuditCategory.from(EventType.APP_SLO)).isEqualTo(AuditCategory.LOGOUT);
    }

    @Test
    void categoriesExposeTheirEventTypes() {
        Set<EventType> sessionTypes = AuditCategory.typesOf(AuditCategory.SESSION);
        assertThat(sessionTypes).contains(EventType.SESSION_CREATED, EventType.SESSION_EXPIRED,
                EventType.SESSION_KICKED, EventType.APP_SESSION_REGISTERED);
        Set<EventType> ticketTypes = AuditCategory.typesOf(AuditCategory.TICKET);
        assertThat(ticketTypes).contains(EventType.TICKET_ISSUED, EventType.TICKET_EXCHANGED,
                EventType.BACKCHANNEL_LOGOUT_FAILED);
        assertThat(AuditCategory.getType("authz")).isEqualTo(AuditCategory.AUTHORIZATION);
        assertThat(AuditCategory.getType("unknown")).isNull();
    }

    @Test
    void allCategoriesTogetherCoverAllEventTypes() {
        Set<EventType> covered = Arrays.stream(AuditCategory.values())
                .flatMap(category -> AuditCategory.typesOf(category).stream())
                .collect(Collectors.toSet());
        assertThat(covered).containsExactlyInAnyOrder(EventType.values());
    }

    @Test
    void newLifecycleCodesResolveAndAreUnique() {
        Set<String> lifecycleCodes = Set.of(EventType.LOGIN_SUCCESS.getCode(),
                EventType.LOGIN_FAILURE.getCode(), EventType.GLOBAL_LOGOUT.getCode(),
                EventType.SESSION_CREATED.getCode(), EventType.SESSION_KICKED.getCode(),
                EventType.TICKET_ISSUED.getCode(), EventType.TICKET_REJECTED.getCode(),
                EventType.AUTHZ_DECISION_ALLOW.getCode(), EventType.AUTHZ_DECISION_DENY.getCode(),
                EventType.POLICY_CREATE.getCode(), EventType.POLICY_UPDATE.getCode(),
                EventType.AUTHZ_ROLE_BIND.getCode(), EventType.SERVICE_ACTION_REGISTER.getCode(),
                EventType.RESOURCE_TYPE_REGISTER.getCode());
        assertThat(lifecycleCodes).hasSize(14);
        assertThat(EventType.getType("pudans:event:session:kicked"))
                .isEqualTo(EventType.SESSION_KICKED);
        assertThat(EventType.getType("pudans:event:authz:policy_updated"))
                .isEqualTo(EventType.POLICY_UPDATE);
        assertThat(EventType.getType("unknown")).isNull();
    }
}