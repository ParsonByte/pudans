package cn.tolim.pudans.mq.enums.converter;

import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class DeadLetterStatusConverter implements AttributeConverter<DeadLetterStatus, String> {

    @Override
    public String convertToDatabaseColumn(DeadLetterStatus attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public DeadLetterStatus convertToEntityAttribute(String dbData) {
        return DeadLetterStatus.fromCode(dbData);
    }
}