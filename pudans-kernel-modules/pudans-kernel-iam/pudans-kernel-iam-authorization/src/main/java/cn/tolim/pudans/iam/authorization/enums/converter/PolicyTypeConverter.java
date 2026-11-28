package cn.tolim.pudans.iam.authorization.enums.converter;

import cn.tolim.pudans.iam.authorization.enums.PolicyType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class PolicyTypeConverter implements AttributeConverter<PolicyType, String> {

    @Override
    public String convertToDatabaseColumn(PolicyType attribute) {
        if (!Objects.isNull(attribute)) {
            return attribute.getCode();
        }
        return null;
    }

    @Override
    public PolicyType convertToEntityAttribute(String dbData) {
        return PolicyType.getType(dbData);
    }
}