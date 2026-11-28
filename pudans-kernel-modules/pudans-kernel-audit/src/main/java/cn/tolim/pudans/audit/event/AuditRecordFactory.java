package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.Actor;
import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.entity.Event;

import java.time.LocalDateTime;
import java.util.List;

public final class AuditRecordFactory {

    private AuditRecordFactory() {
    }

    // Build a persistable audit record from a published audit event
    public static AuditEntity create(AuditEvent auditEvent) {
        Event event = auditEvent.getEvent();
        Actor actor = auditEvent.getActor();
        AuditEntity entity = new AuditEntity();
        entity.setRequestId(auditEvent.getRequestId());
        entity.setSessionId(auditEvent.getSessionId());
        entity.setEventType(event.getType());
        entity.setEventContent(event.getContent());
        entity.setEventParam(event.getParam());
        entity.setEventStatus(event.getStatus());
        entity.setEventResult(event.getResult());
        entity.setEventTime(event.getTime());
        entity.setTargets(auditEvent.getTargets() == null ? null
                : List.copyOf(auditEvent.getTargets()));
        entity.setGeoLocation(auditEvent.getGeoLocation());
        entity.setUserAgent(auditEvent.getUserAgent());
        if (actor != null) {
            entity.setActorId(actor.getId());
            entity.setActorType(actor.getType() == null ? null : actor.getType().getType());
            entity.setActorAuthType(actor.getAuthType());
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = actor == null || actor.getId() == null ? "system" : actor.getId();
        entity.setCreateBy(operator);
        entity.setUpdateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }
}