package cn.tolim.pudans.iam.communal.enums.app.converter;

import cn.tolim.pudans.iam.communal.enums.app.FormEncryptType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class FormEncryptTypeConverter implements AttributeConverter<FormEncryptType, String> {
    @Override
    public String convertToDatabaseColumn(FormEncryptType attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    public FormEncryptType convertToEntityAttribute(String dbData) {
        return FormEncryptType.getType(dbData);
    }
}
