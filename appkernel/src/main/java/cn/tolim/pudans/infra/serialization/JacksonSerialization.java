package cn.tolim.pudans.infra.serialization;

import java.util.ArrayList;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import cn.tolim.pudans.security.authentication.jackson.PudansSecurityJackson3Module;

public final class JacksonSerialization {

    private JacksonSerialization() {}

    public static BasicPolymorphicTypeValidator.Builder polymorphicTypeValidatorBuilder() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("cn.tolim.pudans.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .allowIfSubType("org.springframework.security.")
                .allowIfSubType("org.springframework.session.")
                .allowIfSubTypeIsArray();
    }

    public static PolymorphicTypeValidator polymorphicTypeValidator() {
        return polymorphicTypeValidatorBuilder().build();
    }

    public static JsonMapper jsonMapper() {
        var classLoader = JacksonSerialization.class.getClassLoader();
        var modules = new ArrayList<JacksonModule>(
                SecurityJacksonModules.getModules(classLoader, polymorphicTypeValidatorBuilder()));
        modules.add(new PudansSecurityJackson3Module());
        return JsonMapper.builder().addModules(modules).build();
    }

    public static GenericJacksonJsonRedisSerializer jackson3() {
        return new GenericJacksonJsonRedisSerializer(jsonMapper());
    }

    public static GenericJackson2JsonRedisSerializer jackson2() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.activateDefaultTyping(
                jackson2PolymorphicTypeValidator(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    public static com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator
            jackson2PolymorphicTypeValidator() {
        return com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("cn.tolim.pudans.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .allowIfSubType("org.springframework.security.")
                .allowIfSubType("org.springframework.session.")
                .allowIfSubTypeIsArray()
                .build();
    }

    public static SessionFallbackSerializer sessionSerializer() {
        return new SessionFallbackSerializer(jackson3(), jackson2());
    }

    public static FallbackJsonRedisSerializer cacheSerializer() {
        return new FallbackJsonRedisSerializer(jackson3(), jackson2());
    }
}
