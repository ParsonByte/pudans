package cn.tolim.pudans.iam.communal.jackjson.encrypt;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;


public class EncryptedJsonDeserializer extends JsonDeserializer<Object> {

    private final JsonEncryptType deserializerJsonEncryptType;

    public EncryptedJsonDeserializer(JsonEncryptType deserializer) {
        this.deserializerJsonEncryptType = deserializer;
    }

    @Override
    public Object deserialize(final JsonParser parser,
                              final DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (StringUtils.isBlank(value)) {
            return null;
        }
        if (JsonEncryptType.ENCRYPT == deserializerJsonEncryptType) {
            return EncryptContextHelp.encrypt(value);
        } else if (JsonEncryptType.DECRYPT == deserializerJsonEncryptType) {
            return EncryptContextHelp.decrypt(value);
        }
        return value;
    }
}
