package cn.tolim.pudans.audit.enums.converter;

import cn.tolim.pudans.audit.enums.EventStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter
public class EventStatusConverter implements AttributeConverter<EventStatus, String> {

    @Override
    public String convertToDatabaseColumn(EventStatus attribute) {
        return Objects.isNull(attribute) ? null : attribute.getCode();
    }

    @Override
    public EventStatus convertToEntityAttribute(String dbData) {
        return Objects.isNull(dbData) ? null : EventStatus.getType(dbData);
    }
}