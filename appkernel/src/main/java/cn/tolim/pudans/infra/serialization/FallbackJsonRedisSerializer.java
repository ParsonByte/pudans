package cn.tolim.pudans.infra.serialization;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

public class FallbackJsonRedisSerializer implements RedisSerializer<Object> {

    private final RedisSerializer<Object> primary;

    private final RedisSerializer<Object> legacy;

    // 保存主序列化器与兼容旧格式的备用序列化器。
    public FallbackJsonRedisSerializer(RedisSerializer<Object> primary, RedisSerializer<Object> legacy) {
        this.primary = primary;
        this.legacy = legacy;
    }

    // 写入始终使用主序列化器。
    @Override
    public byte[] serialize(Object value) {
        return primary.serialize(value);
    }

    // 读取时返回解码值，忽略是否来自旧格式。
    @Override
    public Object deserialize(byte[] bytes) {
        return decode(bytes).value();
    }

    // 主格式失败时回退旧格式，并标记需要迁移。
    public Decoded decode(byte[] bytes) {
        if (bytes == null) {
            return new Decoded(null, false);
        }
        try {
            return new Decoded(primary.deserialize(bytes), false);
        } catch (RuntimeException primaryFailure) {
            try {
                return new Decoded(legacy.deserialize(bytes), true);
            } catch (RuntimeException legacyFailure) {
                legacyFailure.addSuppressed(primaryFailure);
                throw new SerializationException("无法使用 Jackson 3 或 Jackson 2 反序列化缓存值",
                        legacyFailure);
            }
        }
    }

    public record Decoded(Object value, boolean legacy) {}
}
