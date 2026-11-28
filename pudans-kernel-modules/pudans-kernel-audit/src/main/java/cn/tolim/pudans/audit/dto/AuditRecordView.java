package cn.tolim.pudans.audit.dto;

import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.entity.GeoLocation;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.entity.UserAgent;
import cn.tolim.pudans.audit.enums.AuditCategory;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.event.type.EventType;

import java.time.LocalDateTime;
import java.util.List;

public record AuditRecordView(String id, String requestId, String sessionId, String actorId,
                              String actorType, String actorAuthType, String eventType,
                              String eventTypeName, String eventCategory, String eventStatus,
                              String eventStatusName, String eventContent, String eventParam,
                              String eventResult, LocalDateTime eventTime, LocalDateTime createTime,
                              List<Target> targets, UserAgent userAgent, GeoLocation geoLocation) {

    public static AuditRecordView from(AuditEntity entity) {
        EventType eventType = entity.getEventType();
        EventStatus eventStatus = entity.getEventStatus();
        return new AuditRecordView(entity.getId(), entity.getRequestId(), entity.getSessionId(),
                entity.getActorId(), entity.getActorType(), entity.getActorAuthType(),
                eventType == null ? null : eventType.getCode(),
                eventType == null ? null : eventType.getDesc(),
                AuditCategory.from(eventType).getCode(),
                eventStatus == null ? null : eventStatus.getCode(),
                eventStatus == null ? null : eventStatus.getDesc(), entity.getEventContent(),
                entity.getEventParam(), entity.getEventResult(), entity.getEventTime(),
                entity.getCreateTime(), entity.getTargets(), entity.getUserAgent(),
                entity.getGeoLocation());
    }
}