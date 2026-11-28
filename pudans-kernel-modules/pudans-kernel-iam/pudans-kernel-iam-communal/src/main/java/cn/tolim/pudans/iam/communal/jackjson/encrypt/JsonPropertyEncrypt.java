package cn.tolim.pudans.iam.communal.jackjson.encrypt;

import com.fasterxml.jackson.annotation.JacksonAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@JacksonAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface JsonPropertyEncrypt {
    JsonEncryptType serializer() default JsonEncryptType.ENCRYPT;

    JsonEncryptType deserializer() default JsonEncryptType.DECRYPT;
}
