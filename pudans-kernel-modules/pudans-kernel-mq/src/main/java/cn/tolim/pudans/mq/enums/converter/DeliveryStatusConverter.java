package cn.tolim.pudans.mq.enums.converter;

import cn.tolim.pudans.mq.enums.DeliveryStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class DeliveryStatusConverter implements AttributeConverter<DeliveryStatus, String> {

    @Override
    public String convertToDatabaseColumn(DeliveryStatus attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public DeliveryStatus convertToEntityAttribute(String dbData) {
        return DeliveryStatus.fromCode(dbData);
    }
}