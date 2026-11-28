package cn.tolim.pudans.iam.infra.security.password.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = true)
public class PasswordComplexityRuleConverter
        implements
        AttributeConverter<PasswordComplexityRule, String> {

    @Override
    public String convertToDatabaseColumn(PasswordComplexityRule attribute) {
        return Objects.isNull(attribute) ? null : attribute.getCode();
    }

    @Override
    public PasswordComplexityRule convertToEntityAttribute(String value) {
        return Objects.isNull(value) ? null : PasswordComplexityRule.getType(value);
    }
}
