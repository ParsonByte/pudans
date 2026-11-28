package cn.tolim.pudans.mq.enums.converter;

import cn.tolim.pudans.mq.enums.MessageStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class MessageStatusConverter implements AttributeConverter<MessageStatus, String> {

    @Override
    public String convertToDatabaseColumn(MessageStatus attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public MessageStatus convertToEntityAttribute(String dbData) {
        return MessageStatus.fromCode(dbData);
    }
}