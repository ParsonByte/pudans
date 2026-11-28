package cn.tolim.pudans.iam.authorization.enums.converter;

import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class AuthorizationStatusConverter implements AttributeConverter<AuthorizationStatus, String> {

    @Override
    public String convertToDatabaseColumn(AuthorizationStatus attribute) {
        if (!Objects.isNull(attribute)) {
            return attribute.getCode();
        }
        return null;
    }

    @Override
    public AuthorizationStatus convertToEntityAttribute(String dbData) {
        return AuthorizationStatus.getStatus(dbData);
    }
}