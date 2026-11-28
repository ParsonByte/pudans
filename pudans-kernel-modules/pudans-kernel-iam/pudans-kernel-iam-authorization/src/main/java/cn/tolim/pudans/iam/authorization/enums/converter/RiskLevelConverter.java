package cn.tolim.pudans.iam.authorization.enums.converter;

import cn.tolim.pudans.iam.authorization.enums.RiskLevel;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class RiskLevelConverter implements AttributeConverter<RiskLevel, String> {

    @Override
    public String convertToDatabaseColumn(RiskLevel attribute) {
        if (!Objects.isNull(attribute)) {
            return attribute.getCode();
        }
        return null;
    }

    @Override
    public RiskLevel convertToEntityAttribute(String dbData) {
        return RiskLevel.getLevel(dbData);
    }
}