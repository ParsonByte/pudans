package cn.tolim.pudans.audit.enums.converter;

import cn.tolim.pudans.audit.enums.TargetType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter
public class TargetTypeConverter implements AttributeConverter<TargetType, String> {

    @Override
    public String convertToDatabaseColumn(TargetType attribute) {
        return Objects.isNull(attribute) ? null : attribute.getCode();
    }

    @Override
    public TargetType convertToEntityAttribute(String dbData) {
        return Objects.isNull(dbData) ? null : TargetType.getType(dbData);
    }
}