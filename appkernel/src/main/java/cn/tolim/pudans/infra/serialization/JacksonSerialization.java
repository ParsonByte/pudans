package cn.tolim.pudans.infra.serialization;

import java.util.ArrayList;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import cn.tolim.pudans.security.authentication.jackson.PudansSecurityJackson3Module;

public final class JacksonSerialization {

    private JacksonSerialization() {}

    // 仅允许受信包参与多态反序列化。
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

    // 组装安全模块与专用模块的 Jackson 3 映射器。
    public static JsonMapper jsonMapper() {
        var classLoader = JacksonSerialization.class.getClassLoader();
        var modules = new ArrayList<JacksonModule>(
                SecurityJacksonModules.getModules(classLoader, polymorphicTypeValidatorBuilder()));
        modules.add(new PudansSecurityJackson3Module());
        return JsonMapper.builder().addModules(modules).build();
    }

    // 创建基于 Jackson 3 的 Redis 序列化器。
    public static GenericJacksonJsonRedisSerializer jackson3() {
        return new GenericJacksonJsonRedisSerializer(jsonMapper());
    }

    // 创建兼容旧缓存数据的 Jackson 2 序列化器。
    public static GenericJackson2JsonRedisSerializer jackson2() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.activateDefaultTyping(
                jackson2PolymorphicTypeValidator(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    // 为旧版映射器限定可反序列化类型范围。
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

    // 会话读取同时兼容 Jackson 3 与旧 JSON。
    public static SessionFallbackSerializer sessionSerializer() {
        return new SessionFallbackSerializer(jackson3(), jackson2());
    }

    // 缓存读取兼容旧 JSON 并支持迁移。
    public static FallbackJsonRedisSerializer cacheSerializer() {
        return new FallbackJsonRedisSerializer(jackson3(), jackson2());
    }
}
