package cn.tolim.pudans.audit.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;

import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;

import lombok.Getter;

@Getter
public enum AuditCategory {
    LOGIN("login", "登录"),
    LOGOUT("logout", "登出"),
    SESSION("session", "登录会话"),
    TICKET("ticket", "SSO票据"),
    AUTHORIZATION("authz", "统一授权"),
    ACCOUNT("account", "账号与组织"),
    APPLICATION("application", "应用管理"),
    SETTING("setting", "系统设置"),
    MONITOR("monitor", "运维监控"),
    OTHER("other", "其他");

    private static final Map<AuditCategory, Set<EventType>> EVENT_TYPES = buildEventTypes();

    @JsonValue
    private final String code;
    private final String desc;

    AuditCategory(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Resolve an event type to its audit category
    public static AuditCategory from(EventType eventType) {
        if (eventType == null) {
            return OTHER;
        }
        String code = eventType.getCode();
        if (code.contains(":login")) {
            return LOGIN;
        }
        if (code.contains(":logout")) {
            return LOGOUT;
        }
        if ("app_slo".equals(code)) {
            return LOGOUT;
        }
        if (code.contains(":session")) {
            return SESSION;
        }
        if (code.contains(":ticket") || code.contains(":backchannel") || "app_sso".equals(code)) {
            return TICKET;
        }
        if (code.contains(":authz") || code.contains(":policy") || code.contains(":role")
                || code.contains(":service_action") || code.contains(":resource_type")) {
            return AUTHORIZATION;
        }
        if (code.contains(":account")) {
            return ACCOUNT;
        }
        if (code.contains(":setting")) {
            return SETTING;
        }
        if (code.contains(":monitor")) {
            return MONITOR;
        }
        if (code.contains("app")) {
            return APPLICATION;
        }
        return OTHER;
    }

    // All event types that belong to the given category
    public static Set<EventType> typesOf(AuditCategory category) {
        if (category == null) {
            return Set.of();
        }
        return EVENT_TYPES.getOrDefault(category, Set.of());
    }

    @EnumConvert
    public static AuditCategory getType(String code) {
        AuditCategory[] values = values();
        for (AuditCategory category : values) {
            if (category.getCode().equalsIgnoreCase(code)) {
                return category;
            }
        }
        return null;
    }

    private static Map<AuditCategory, Set<EventType>> buildEventTypes() {
        Map<AuditCategory, Set<EventType>> mutable = new EnumMap<>(AuditCategory.class);
        for (AuditCategory category : values()) {
            mutable.put(category, EnumSet.noneOf(EventType.class));
        }
        for (EventType eventType : EventType.values()) {
            mutable.get(from(eventType)).add(eventType);
        }
        Map<AuditCategory, Set<EventType>> immutable = new EnumMap<>(AuditCategory.class);
        mutable.forEach((category, types) -> immutable.put(category, Collections.unmodifiableSet(types)));
        return Collections.unmodifiableMap(immutable);
    }
}