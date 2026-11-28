package cn.tolim.pudans.iam.authorization.enums.converter;

import cn.tolim.pudans.iam.authorization.enums.PolicyDecision;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class PolicyDecisionConverter implements AttributeConverter<PolicyDecision, String> {

    @Override
    public String convertToDatabaseColumn(PolicyDecision attribute) {
        if (!Objects.isNull(attribute)) {
            return attribute.getCode();
        }
        return null;
    }

    @Override
    public PolicyDecision convertToEntityAttribute(String dbData) {
        return PolicyDecision.getDecision(dbData);
    }
}