package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.Actor;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.enums.AuditCategory;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.enums.TargetType;
import cn.tolim.pudans.audit.event.type.EventType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuditEventRecorder {

    private final AuditEventPublish auditEventPublish;

    public AuditEventRecorder(AuditEventPublish auditEventPublish) {
        this.auditEventPublish = auditEventPublish;
    }

    // Record a successful password, MFA or social login that created a global session
    public void recordLoginSuccess(Authentication authentication, String appCode, String sid) {
        auditEventPublish.publish(EventType.LOGIN_SUCCESS, authentication, EventStatus.SUCCESS,
                targets(applicationTarget(appCode), sessionTarget(sid)));
    }

    public void recordLoginFailure(String username, String reason, String appCode) {
        recordAuthenticationFailure(EventType.LOGIN_FAILURE, username, reason, appCode);
    }

    public void recordLoginMfaFailure(String username, String reason, String appCode) {
        recordAuthenticationFailure(EventType.LOGIN_MFA_FAILURE, username, reason, appCode);
    }

    public void recordLoginLocked(String username, String reason, String appCode) {
        recordAuthenticationFailure(EventType.LOGIN_LOCKED, username, reason, appCode);
    }

    // Record a global logout; all applications are notified through back-channel logout
    public void recordLogout(Actor actor, String sid) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "sid", sid);
        publish(EventType.GLOBAL_LOGOUT, actor, targets(sessionTarget(sid)), parameters,
                EventType.GLOBAL_LOGOUT.getDesc(), EventStatus.SUCCESS.getCode(),
                EventStatus.SUCCESS);
    }

    // Record a global login session lifecycle change, for example created, expired or revoked
    public void recordSessionEvent(EventType eventType, Actor actor, String sid, String reason,
                                   EventStatus status) {
        assertCategory(eventType, AuditCategory.SESSION);
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "sid", sid);
        putIfPresent(parameters, "reason", reason);
        publish(eventType, actor, targets(sessionTarget(sid)), parameters, eventType.getDesc(), reason,
                status);
    }

    // Record an application session cache lifecycle change bound to a global session
    public void recordAppSessionEvent(EventType eventType, Actor actor, String appCode, String sid,
                                      String reason, EventStatus status) {
        assertCategory(eventType, AuditCategory.SESSION);
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "appCode", appCode);
        putIfPresent(parameters, "sid", sid);
        putIfPresent(parameters, "reason", reason);
        publish(eventType, actor, targets(applicationTarget(appCode), sessionTarget(sid)),
                parameters, eventType.getDesc(), reason, status);
    }

    public void recordTicketIssued(Actor actor, String appCode, String sid) {
        recordTicketEvent(EventType.TICKET_ISSUED, actor, appCode, sid, null, EventStatus.SUCCESS);
    }

    public void recordTicketExchanged(Actor actor, String appCode, String sid) {
        recordTicketEvent(EventType.TICKET_EXCHANGED, actor, appCode, sid, null, EventStatus.SUCCESS);
    }

    // Record a rejected ticket without persisting the ticket value itself
    public void recordTicketRejected(Actor actor, String appCode, String reason) {
        recordTicketEvent(EventType.TICKET_REJECTED, actor, appCode, null, reason, EventStatus.FAIL);
    }

    public void recordBackchannelLogout(EventType eventType, Actor actor, String appCode, String sid,
                                        String result, EventStatus status) {
        assertCategory(eventType, AuditCategory.TICKET);
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "appCode", appCode);
        putIfPresent(parameters, "sid", sid);
        putIfPresent(parameters, "result", result);
        publish(eventType, actor, targets(applicationTarget(appCode), sessionTarget(sid)),
                parameters, eventType.getDesc(), result, status);
    }

    // Record an allow or deny authorization decision with action, resource and reason
    public void recordAuthorizationDecision(Actor actor, String action, String resource,
                                            boolean allowed, String reason) {
        EventType eventType = allowed ? EventType.AUTHZ_DECISION_ALLOW : EventType.AUTHZ_DECISION_DENY;
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "action", action);
        putIfPresent(parameters, "resource", resource);
        parameters.put("decision", allowed ? "allow" : "deny");
        putIfPresent(parameters, "reason", reason);
        String content = action + " on " + (StringUtils.hasText(resource) ? resource : "*") + ": "
                + (allowed ? "allow" : "deny");
        publish(eventType, actor, null, parameters, content, reason,
                allowed ? EventStatus.SUCCESS : EventStatus.FAIL);
    }

    public void recordPolicyChange(EventType eventType, Actor actor, String policyId,
                                   String policyName) {
        assertAuthzEvent(eventType, "policy_");
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "policyId", policyId);
        putIfPresent(parameters, "policyName", policyName);
        Target target = StringUtils.hasText(policyId)
                ? Target.builder().id(policyId).name(policyName).type(TargetType.POLICY).build()
                : null;
        publish(eventType, actor, targets(target), parameters, eventType.getDesc(), null,
                EventStatus.SUCCESS);
    }

    public void recordPolicyAttachmentChange(EventType eventType, Actor actor, String policyId,
                                             String principalType, String principalId) {
        assertAuthzEvent(eventType, "policy_");
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "policyId", policyId);
        putIfPresent(parameters, "principalType", principalType);
        putIfPresent(parameters, "principalId", principalId);
        Target target = StringUtils.hasText(policyId)
                ? Target.builder().id(policyId).name(policyId).type(TargetType.POLICY_ATTACHMENT)
                .build()
                : null;
        publish(eventType, actor, targets(target), parameters, eventType.getDesc(), null,
                EventStatus.SUCCESS);
    }

    public void recordRoleChange(EventType eventType, Actor actor, String roleId, String roleName) {
        assertAuthzEvent(eventType, "role_");
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "roleId", roleId);
        putIfPresent(parameters, "roleName", roleName);
        Target target = StringUtils.hasText(roleId)
                ? Target.builder().id(roleId).name(roleName).type(TargetType.AUTHZ_ROLE).build()
                : null;
        publish(eventType, actor, targets(target), parameters, eventType.getDesc(), null,
                EventStatus.SUCCESS);
    }

    public void recordServiceActionRegistration(Actor actor, String appCode, String action,
                                                String resourceType) {
        assertAuthzEvent(EventType.SERVICE_ACTION_REGISTER, "service_action");
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "appCode", appCode);
        putIfPresent(parameters, "action", action);
        putIfPresent(parameters, "resourceType", resourceType);
        Target target = StringUtils.hasText(action)
                ? Target.builder().id(action).name(action).type(TargetType.SERVICE_ACTION).build()
                : null;
        publish(EventType.SERVICE_ACTION_REGISTER, actor, targets(target), parameters,
                EventType.SERVICE_ACTION_REGISTER.getDesc(), null, EventStatus.SUCCESS);
    }

    public void recordResourceTypeRegistration(Actor actor, String appCode, String resourceType,
                                               String pattern) {
        assertAuthzEvent(EventType.RESOURCE_TYPE_REGISTER, "resource_type");
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "appCode", appCode);
        putIfPresent(parameters, "resourceType", resourceType);
        putIfPresent(parameters, "pattern", pattern);
        Target target = StringUtils.hasText(resourceType)
                ? Target.builder().id(resourceType).name(pattern)
                .type(TargetType.RESOURCE_TYPE).build()
                : null;
        publish(EventType.RESOURCE_TYPE_REGISTER, actor, targets(target), parameters,
                EventType.RESOURCE_TYPE_REGISTER.getDesc(), null, EventStatus.SUCCESS);
    }

    private void recordAuthenticationFailure(EventType eventType, String username, String reason,
                                             String appCode) {
        Actor actor = Actor.builder().id(username).build();
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "username", username);
        putIfPresent(parameters, "reason", reason);
        putIfPresent(parameters, "appCode", appCode);
        String content = StringUtils.hasText(username) ? username + "：" + eventType.getDesc()
                : eventType.getDesc();
        publish(eventType, actor, targets(applicationTarget(appCode)), parameters, content, reason,
                EventStatus.FAIL);
    }

    private void recordTicketEvent(EventType eventType, Actor actor, String appCode, String sid,
                                   String reason, EventStatus status) {
        assertCategory(eventType, AuditCategory.TICKET);
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "appCode", appCode);
        putIfPresent(parameters, "sid", sid);
        putIfPresent(parameters, "reason", reason);
        publish(eventType, actor, targets(applicationTarget(appCode), sessionTarget(sid)),
                parameters, eventType.getDesc(), reason, status);
    }

    private void publish(EventType eventType, Actor actor, List<Target> targets,
                         Map<String, Object> parameters, String content, String result,
                         EventStatus status) {
        auditEventPublish.publish(eventType, parameters, content, targets, result, status, actor);
    }

    private static void assertCategory(EventType eventType, AuditCategory category) {
        Assert.notNull(eventType, "eventType must not be null");
        Assert.isTrue(AuditCategory.from(eventType) == category,
                "Event type " + eventType + " does not belong to category " + category.getCode());
    }

    private static void assertAuthzEvent(EventType eventType, String codeFragment) {
        Assert.notNull(eventType, "eventType must not be null");
        Assert.isTrue(AuditCategory.from(eventType) == AuditCategory.AUTHORIZATION
                        && eventType.getCode().contains(codeFragment),
                "Event type " + eventType + " is not an authorization " + codeFragment + " event");
    }

    private static void putIfPresent(Map<String, Object> parameters, String key, String value) {
        if (StringUtils.hasText(value)) {
            parameters.put(key, value);
        }
    }

    private static Target applicationTarget(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return null;
        }
        return Target.builder().id(appCode).name(appCode).type(TargetType.APPLICATION).build();
    }

    private static Target sessionTarget(String sid) {
        if (!StringUtils.hasText(sid)) {
            return null;
        }
        return Target.builder().id(sid).name("登录会话").type(TargetType.LOGIN_SESSION).build();
    }

    private static List<Target> targets(Target... targets) {
        List<Target> values = new ArrayList<>();
        for (Target target : targets) {
            if (target != null) {
                values.add(target);
            }
        }
        return values.isEmpty() ? null : List.copyOf(values);
    }
}