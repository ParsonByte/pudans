package cn.tolim.pudans.audit.query;

import cn.tolim.pudans.audit.entity.AuditEntity;
import cn.tolim.pudans.audit.enums.AuditCategory;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.event.type.EventType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AuditSpecifications {

    private AuditSpecifications() {
    }

    // Build the JPA specification for all supported audit query filters
    public static Specification<AuditEntity> of(AuditQuery query) {
        Assert.notNull(query, "query must not be null");
        Set<EventType> eventTypes = resolveEventTypes(query);
        if (eventTypes != null && eventTypes.isEmpty()) {
            return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.disjunction();
        }
        List<Specification<AuditEntity>> specifications = new ArrayList<>();
        if (eventTypes != null) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> root.get("eventType")
                    .in(eventTypes));
        }
        if (StringUtils.hasText(query.getEventStatus())) {
            EventStatus eventStatus = EventStatus.getType(query.getEventStatus());
            if (eventStatus == null) {
                throw new IllegalArgumentException(
                        "Unknown audit event status: " + query.getEventStatus());
            }
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .equal(root.get("eventStatus"), eventStatus));
        }
        if (StringUtils.hasText(query.getActorId())) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .equal(root.get("actorId"), query.getActorId()));
        }
        if (StringUtils.hasText(query.getActorType())) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .equal(root.get("actorType"), query.getActorType()));
        }
        if (StringUtils.hasText(query.getActorAuthType())) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .equal(root.get("actorAuthType"), query.getActorAuthType()));
        }
        if (StringUtils.hasText(query.getRequestId())) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .equal(root.get("requestId"), query.getRequestId()));
        }
        if (StringUtils.hasText(query.getSessionId())) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .equal(root.get("sessionId"), query.getSessionId()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String pattern = "%" + query.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("eventContent")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("eventParam")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("eventResult")), pattern)));
        }
        LocalDateTime startTime = query.getStartTime();
        LocalDateTime endTime = query.getEndTime();
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Audit start time must not be after end time");
        }
        if (startTime != null) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .greaterThanOrEqualTo(root.get("eventTime"), startTime));
        }
        if (endTime != null) {
            specifications.add((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder
                    .lessThanOrEqualTo(root.get("eventTime"), endTime));
        }
        if (specifications.isEmpty()) {
            return Specification.unrestricted();
        }
        return Specification.allOf(specifications);
    }

    // Resolve explicit event types and category into one set, or null when unfiltered
    private static Set<EventType> resolveEventTypes(AuditQuery query) {
        Set<EventType> explicit = null;
        if (StringUtils.hasText(query.getEventType())
                || !CollectionUtils.isEmpty(query.getEventTypes())) {
            explicit = EnumSet.noneOf(EventType.class);
            if (StringUtils.hasText(query.getEventType())) {
                explicit.add(parseEventType(query.getEventType()));
            }
            if (query.getEventTypes() != null) {
                for (String code : query.getEventTypes()) {
                    if (StringUtils.hasText(code)) {
                        explicit.add(parseEventType(code));
                    }
                }
            }
        }
        Set<EventType> categoryTypes = null;
        if (StringUtils.hasText(query.getCategory())) {
            AuditCategory category = AuditCategory.getType(query.getCategory());
            if (category == null) {
                throw new IllegalArgumentException(
                        "Unknown audit category: " + query.getCategory());
            }
            categoryTypes = AuditCategory.typesOf(category);
        }
        if (explicit == null) {
            return categoryTypes;
        }
        if (categoryTypes == null) {
            return explicit;
        }
        explicit.retainAll(categoryTypes);
        return explicit;
    }

    private static EventType parseEventType(String code) {
        EventType eventType = EventType.getType(code);
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown audit event type: " + code);
        }
        return eventType;
    }
}