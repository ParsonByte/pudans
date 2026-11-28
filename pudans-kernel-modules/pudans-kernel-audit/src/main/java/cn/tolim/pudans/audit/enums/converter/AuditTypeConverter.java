package cn.tolim.pudans.audit.enums.converter;

import cn.tolim.pudans.audit.event.type.EventType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter
public class AuditTypeConverter implements AttributeConverter<EventType, String> {

    @Override
    public String convertToDatabaseColumn(EventType attribute) {
        return Objects.isNull(attribute) ? null : attribute.getCode();
    }

    @Override
    public EventType convertToEntityAttribute(String dbData) {
        return Objects.isNull(dbData) ? null : EventType.getType(dbData);
    }
}