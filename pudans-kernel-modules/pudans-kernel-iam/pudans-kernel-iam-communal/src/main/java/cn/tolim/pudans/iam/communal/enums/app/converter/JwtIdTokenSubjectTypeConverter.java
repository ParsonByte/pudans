package cn.tolim.pudans.iam.communal.enums.app.converter;

import cn.tolim.pudans.iam.communal.enums.app.JwtIdTokenSubjectType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class JwtIdTokenSubjectTypeConverter implements
        AttributeConverter<JwtIdTokenSubjectType, String> {
    @Override
    public String convertToDatabaseColumn(JwtIdTokenSubjectType attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public JwtIdTokenSubjectType convertToEntityAttribute(String dbData) {
        return JwtIdTokenSubjectType.getType(dbData);
    }
}
