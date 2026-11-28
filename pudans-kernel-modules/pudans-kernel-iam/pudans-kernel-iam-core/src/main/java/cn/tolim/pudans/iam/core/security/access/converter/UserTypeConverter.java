package cn.tolim.pudans.iam.core.security.access.converter;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

import static cn.tolim.pudans.iam.infra.security.userdetails.UserType.ADMIN;
import static cn.tolim.pudans.iam.infra.security.userdetails.UserType.USER;


@Converter(autoApply = true)
public class UserTypeConverter implements AttributeConverter<UserType, String> {

    // Converts the value stored in the entity attribute into the
    // data representation to be stored in the database.
    // Parameter attribute: the entity attribute value to be converted
    // Returns: the converted data to be stored in the database
    // column
    @Override
    public String convertToDatabaseColumn(UserType attribute) {
        if (Objects.isNull(attribute)) {
            return null;
        }
        return attribute.getType();
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
    public UserType convertToEntityAttribute(String dbData) {
        if (dbData.equals(ADMIN.getType())) {
            return ADMIN;
        }
        if (dbData.equals(USER.getType())) {
            return USER;
        }
        throw new PudansException("未知用户类型");
    }
}
