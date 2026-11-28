package cn.tolim.pudans.iam.communal.jackjson.encrypt;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;


public class EncryptedDeserializerModifier extends BeanDeserializerModifier {

    private final JsonEncryptType jsonEncryptType;

    public EncryptedDeserializerModifier() {
        this.jsonEncryptType = null;
    }

    public EncryptedDeserializerModifier(JsonEncryptType jsonEncryptType) {
        this.jsonEncryptType = jsonEncryptType;
    }

    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config,
                                                 BeanDescription beanDesc,
                                                 BeanDeserializerBuilder builder) {
        var properties = builder.getProperties();
        while (properties.hasNext()) {
            var property = properties.next();
            JsonPropertyEncrypt annotation = property.getAnnotation(JsonPropertyEncrypt.class);
            if (annotation != null) {
                JsonEncryptType deserializer = jsonEncryptType;
                if (jsonEncryptType == null) {
                    deserializer = annotation.deserializer();
                }
                builder.addOrReplaceProperty(
                        property.withValueDeserializer(new EncryptedJsonDeserializer(deserializer)),
                        true);
            }
        }
        return builder;
    }
}
