package cn.tolim.pudans.iam.authentication.core;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class IdentityProviderCategoryConverter implements
        AttributeConverter<IdentityProviderCategory, String> {

    // Converts the value stored in the entity attribute into the
    // data representation to be stored in the database.
    @Override
    public String convertToDatabaseColumn(IdentityProviderCategory attribute) {
        if (!Objects.isNull(attribute)) {
            return attribute.getCode();
        }
        return null;
    }

    // Converts the data stored in the database column into the
    // value to be stored in the entity attribute.
    // Note that it is the responsibility of the converter writer to
    // specify the correct dbData type for the corresponding
    // column for use by the JDBC driver: i.e., persistence providers are
    // not expected to do such type conversion.
    @Override
    public IdentityProviderCategory convertToEntityAttribute(String dbData) {
        return IdentityProviderCategory.getType(dbData);
    }
}
