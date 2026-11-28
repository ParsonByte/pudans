package cn.tolim.pudans.mq.enums.converter;

import cn.tolim.pudans.mq.enums.CompensationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class CompensationStatusConverter implements AttributeConverter<CompensationStatus, String> {

    @Override
    public String convertToDatabaseColumn(CompensationStatus attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public CompensationStatus convertToEntityAttribute(String dbData) {
        return CompensationStatus.fromCode(dbData);
    }
}