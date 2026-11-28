package cn.tolim.pudans.iam.communal.enums.app.converter;

import cn.tolim.pudans.iam.communal.enums.app.JwtBindingType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class JwtBindingTypeConverter implements AttributeConverter<JwtBindingType, String> {
    @Override
    public String convertToDatabaseColumn(JwtBindingType attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public JwtBindingType convertToEntityAttribute(String dbData) {
        return JwtBindingType.getType(dbData);
    }
}
