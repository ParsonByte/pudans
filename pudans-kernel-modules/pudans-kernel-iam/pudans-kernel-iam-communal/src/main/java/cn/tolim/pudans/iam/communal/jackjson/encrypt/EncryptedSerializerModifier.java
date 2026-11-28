package cn.tolim.pudans.iam.communal.jackjson.encrypt;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.ArrayList;
import java.util.List;


public class EncryptedSerializerModifier extends BeanSerializerModifier {

    private final JsonEncryptType jsonEncryptType;

    public EncryptedSerializerModifier() {
        this.jsonEncryptType = null;
    }

    public EncryptedSerializerModifier(JsonEncryptType jsonEncryptType) {
        this.jsonEncryptType = jsonEncryptType;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        // Iterate bean properties and process Encrypt.class annotations
        List<BeanPropertyWriter> newWriter = new ArrayList<>();
        for (BeanPropertyWriter writer : beanProperties) {
            JsonPropertyEncrypt annotation = writer.getAnnotation(JsonPropertyEncrypt.class);
            if (null == annotation) {
                newWriter.add(writer);
            } else {
                JsonEncryptType deserializer = jsonEncryptType;
                if (jsonEncryptType == null) {
                    deserializer = annotation.deserializer();
                }
                JsonSerializer<Object> serializer = new EncryptedJsonSerializer(
                        writer.getSerializer(), deserializer);
                writer.assignSerializer(serializer);
                newWriter.add(writer);
            }
        }

        return newWriter;
    }
}
