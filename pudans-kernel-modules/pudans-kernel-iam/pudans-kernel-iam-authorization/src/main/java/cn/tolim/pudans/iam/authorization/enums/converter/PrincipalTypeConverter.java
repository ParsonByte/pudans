package cn.tolim.pudans.iam.authorization.enums.converter;

import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class PrincipalTypeConverter implements AttributeConverter<PrincipalType, String> {

    @Override
    public String convertToDatabaseColumn(PrincipalType attribute) {
        if (!Objects.isNull(attribute)) {
            return attribute.getCode();
        }
        return null;
    }

    @Override
    public PrincipalType convertToEntityAttribute(String dbData) {
        return PrincipalType.getType(dbData);
    }
}