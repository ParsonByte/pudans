package cn.tolim.pudans.iam.communal.enums.account.converter;

import cn.tolim.pudans.iam.communal.enums.UserStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;


@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    // Converts the value stored in the entity attribute into the
    // data representation to be stored in the database.
    // Parameter attribute: the entity attribute value to be converted
    // Returns: the converted data to be stored in the database
    // column
    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
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
    // Parameter dbData: the data from the database column to be
    // converted
    // Returns: the converted value to be stored in the entity
    // attribute
    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        return UserStatus.getStatus(dbData);
    }
}
