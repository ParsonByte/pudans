package cn.tolim.pudans.iam.communal.jackjson.encrypt;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.StringWriter;


public class EncryptedJsonSerializer extends JsonSerializer<Object> {

    // Default serialization tool object
    private final JsonSerializer<Object> serializer;
    private final JsonEncryptType serializerJsonEncryptType;

    public EncryptedJsonSerializer() {
        this.serializer = null;
        this.serializerJsonEncryptType = null;
    }

    public EncryptedJsonSerializer(JsonSerializer<Object> serializer,
                                   JsonEncryptType jsonEncryptType) {
        this.serializer = serializer;
        this.serializerJsonEncryptType = jsonEncryptType;
    }

    @Override
    public void serialize(Object obj, JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) throws IOException {
        StringWriter stringWriter = new StringWriter();
        ObjectCodec objectCodec = jsonGenerator.getCodec();
        JsonGenerator nestedGenerator = null;

        // Do not process null objects or empty strings.
        if (obj == null || StringUtils.isEmpty(String.valueOf(obj))) {
            if (serializer == null) {
                serializerProvider.defaultSerializeValue(obj, jsonGenerator);
            } else {
                serializer.serialize(obj, jsonGenerator, serializerProvider);
            }
            return;
        }
        // Create a new JsonGenerator for writing obj.
        if (objectCodec instanceof ObjectMapper) {
            nestedGenerator = objectCodec.getFactory().createGenerator(stringWriter);
        }

        if (nestedGenerator == null) {
            throw new NullPointerException("nestedGenerator 不能为空");
        }

        // Write data to the new JsonGenerator
        if (serializer == null) {
            serializerProvider.defaultSerializeValue(obj, nestedGenerator);
        } else {
            serializer.serialize(obj, nestedGenerator, serializerProvider);
        }

        nestedGenerator.close();
        // JsonGenerator emits a double-quoted string; write the encrypted data.
        String value = stringWriter.getBuffer().toString();
        try {
            String newValue = value.substring(1, value.length() - 1);
            if (StringUtils.isNotEmpty(newValue)) {
                if (JsonEncryptType.ENCRYPT == serializerJsonEncryptType) {
                    newValue = EncryptContextHelp.encrypt(newValue);
                } else if (JsonEncryptType.DECRYPT == serializerJsonEncryptType) {
                    newValue = EncryptContextHelp.decrypt(value);
                }
            }
            jsonGenerator.writeString(newValue);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
