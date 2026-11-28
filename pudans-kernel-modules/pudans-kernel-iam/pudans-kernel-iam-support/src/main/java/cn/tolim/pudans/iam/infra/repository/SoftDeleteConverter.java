package cn.tolim.pudans.iam.infra.repository;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SoftDeleteConverter implements AttributeConverter<Boolean, Boolean> {
    @Override
    public Boolean convertToDatabaseColumn(Boolean deleted) {
        if (deleted == null) {
            return Boolean.FALSE;
        }
        return deleted;
    }

    @Override
    public Boolean convertToEntityAttribute(Boolean value) {
        if (value == null) {
            return false;
        }
        return value;
    }
}
