package cn.tolim.pudans.audit.dto;

import cn.tolim.pudans.audit.enums.AuditCategory;
import cn.tolim.pudans.audit.event.Resource;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;

import java.util.List;

public record AuditEventTypeView(String code, String name, String category, String categoryName,
                                 String resourceCode, String resourceName, List<UserType> userTypes) {

    public static AuditEventTypeView from(EventType eventType) {
        Resource resource = eventType.getResource();
        AuditCategory category = AuditCategory.from(eventType);
        return new AuditEventTypeView(eventType.getCode(), eventType.getDesc(), category.getCode(),
                category.getDesc(), resource == null ? null : resource.getCode(),
                resource == null ? null : resource.getName(), eventType.getUserTypes());
    }
}